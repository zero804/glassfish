/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.enterprise.v3.services.impl;

import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.VirtualServer;
import com.sun.enterprise.util.Result;
import com.sun.enterprise.v3.services.impl.GrizzlyProxy.GrizzlyFuture;
import com.sun.grizzly.config.dom.FileCache;
import com.sun.grizzly.config.dom.Http;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.Ssl;
import com.sun.grizzly.config.dom.ThreadPool;
import com.sun.grizzly.config.dom.Transport;
import org.jvnet.hk2.config.Changed;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.ConfigListener;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.NotProcessed;
import org.jvnet.hk2.config.UnprocessedChangeEvents;

/**
 * Grizzly dynamic configuration handler
 *
 * @author Alexey Stashok
 */
public class DynamicConfigListener implements ConfigListener {
    private static final String ADMIN_LISTENER = "admin-listener";
    private GrizzlyService grizzlyService;
    private String config;
    private Logger logger;
    private static final int RECONFIG_LOCK_TIMEOUT_SEC = 30;
    private static final ReentrantLock reconfigLock = new ReentrantLock();
    private static final Map<Integer, GrizzlyFuture> reconfigByPortLock = new HashMap<Integer, GrizzlyFuture>();

    public DynamicConfigListener(final Config parent) {
        config = findConfigName(parent);
    }

    @Override
    public UnprocessedChangeEvents changed(final PropertyChangeEvent[] events) {
        return ConfigSupport.sortAndDispatch(
            events, new Changed() {
                @Override
                public <T extends ConfigBeanProxy> NotProcessed changed(TYPE type,
                    Class<T> tClass, T t) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "NetworkConfig changed " + type + " " + tClass + " " + t);
                    }
                    if (t instanceof NetworkListener) {
                        return processNetworkListener(type, (NetworkListener) t, events);
                    } else if (t instanceof Http) {
                        return processProtocol(type, (Protocol) t.getParent(), events);
                    } else if (t instanceof FileCache) {
                        return processProtocol(type, (Protocol) t.getParent().getParent(), null);
                    } else if (t instanceof Ssl) {
                        return processProtocol(type, (Protocol) t.getParent(), null);
                    } else if (t instanceof Protocol) {
                        return processProtocol(type, (Protocol) t, null);
                    } else if (t instanceof ThreadPool) {
                        NotProcessed notProcessed = null;
                        for (NetworkListener listener : ((ThreadPool) t).findNetworkListeners()) {
                            notProcessed = processNetworkListener(type, listener, null);
                        }
                        return notProcessed;
                    } else if (t instanceof Transport) {
                        NotProcessed notProcessed = null;
                        for (NetworkListener listener : ((Transport) t).findNetworkListeners()) {
                            notProcessed = processNetworkListener(type, listener, null);
                        }
                        return notProcessed;
                    } else if (t instanceof VirtualServer && !grizzlyService.hasMapperUpdateListener()) {
                        return processVirtualServer(type, (VirtualServer) t);
                    }
                    return null;
                }
            }, logger);
    }

    private <T extends ConfigBeanProxy> NotProcessed processNetworkListener(Changed.TYPE type,
        NetworkListener listener, PropertyChangeEvent[] changedProperties) {
        if (findConfigName(listener).equals(config)) {
            boolean isAdminListener = ADMIN_LISTENER.equals(listener.getName());
            Lock portLock = null;
            try {
                portLock = acquirePortLock(listener);
                if (type == Changed.TYPE.ADD) {
                    final Future future = grizzlyService.createNetworkProxy(listener);
                    future.get(RECONFIG_LOCK_TIMEOUT_SEC, TimeUnit.SECONDS);
                    grizzlyService.registerNetworkProxy();
                } else if (type == Changed.TYPE.REMOVE) {
                    if (!isAdminListener) {
                        grizzlyService.removeNetworkProxy(listener);
                    }
                } else if (type == Changed.TYPE.CHANGE) {
                    if (isAdminListener) {
                        final boolean dynamic = isAdminDynamic(changedProperties);
                        if (dynamic) {
                            GrizzlyProxy proxy = (GrizzlyProxy) grizzlyService.lookupNetworkProxy(listener);
                            if (proxy != null) {
                                GrizzlyListener netListener = proxy.getUnderlyingListener();
                                netListener.processDynamicConfigurationChange(changedProperties);
                                return null;
                            }
                        }
                        return null;
                    }
                    // Restart GrizzlyProxy on the address/port
                    // Address/port/id could have been changed - so try to find
                    // corresponding proxy both ways
                    if (!grizzlyService.removeNetworkProxy(listener)) {
                        grizzlyService.removeNetworkProxy(listener.getName());
                    }
                    final Future future = grizzlyService.createNetworkProxy(listener);
                    if (future != null) {
                        future.get(30, TimeUnit.SECONDS);
                        grizzlyService.registerNetworkProxy();
                    } else {
                        logger.log(Level.FINE, "Skipping proxy registration for the listener " + listener.getName());
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Network listener configuration error. Type: " + type, e);
            } finally {
                if (portLock != null) {
                    releaseListenerLock(portLock);
                }
            }
        } else {
            System.out.println(listener.getName() + " not part of the original config");
        }
        return null;
    }

    private String findConfigName(final ConfigBeanProxy child) {
        ConfigBeanProxy bean = child;
        while(bean != null && ! (bean instanceof Config)) {
            bean = bean.getParent();
        }
        return bean instanceof Config ? ((Config) bean).getName() : "";
    }

    private boolean isAdminDynamic(PropertyChangeEvent[] events) {
        if (events == null || events.length == 0) {
            return false;
        }
        // for now, anything other than comet support will require a restart
        for (PropertyChangeEvent e : events) {
            if ("comet-support-enabled".equals(e.getPropertyName())) {
                return true;
            }
        }
        return false;
    }

    private NotProcessed processProtocol(Changed.TYPE type, Protocol protocol, PropertyChangeEvent[] events) {
        NotProcessed notProcessed = null;
        for (NetworkListener listener : protocol.findNetworkListeners()) {
            notProcessed = processNetworkListener(type, listener, events);
        }
        return notProcessed;
    }

    private NotProcessed processVirtualServer(Changed.TYPE type, VirtualServer vs) {
        NotProcessed notProcessed = null;
        String list = vs.getNetworkListeners();
        for (String s : GrizzlyProxy.toArray(list, ",")) {
            for (NetworkListener n : vs.findNetworkListeners()) {
                if (n.getName().equals(s)) {
                    notProcessed = processNetworkListener(type, n, null);
                }
            }
        }
        return notProcessed;
    }

    public void setGrizzlyService(GrizzlyService grizzlyService) {
        this.grizzlyService = grizzlyService;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Lock TCP ports, which will take part in the reconfiguration to avoid collisions
     */
    private Lock acquirePortLock(NetworkListener listener) throws InterruptedException, TimeoutException {
        final boolean isLoggingFinest = logger.isLoggable(Level.FINEST);
        final int port = getPort(listener);
        try {
            while (true) {
                logger.finest("Aquire reconfig lock");
                if (reconfigLock.tryLock(RECONFIG_LOCK_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                    Future lock = reconfigByPortLock.get(port);
                    if (isLoggingFinest) {
                        logger.finest("Reconfig lock for port: " + port + " is " + lock);
                    }
                    int proxyPort = -1;
                    if (lock == null) {
                        final NetworkProxy runningProxy = grizzlyService.lookupNetworkProxy(listener);
                        if (runningProxy != null) {
                            proxyPort = runningProxy.getPort();
                            if (port != proxyPort) {
                                lock = reconfigByPortLock.get(proxyPort);
                                if (isLoggingFinest) {
                                    logger.finest("Reconfig lock for proxyport: " + proxyPort + " is " + lock);
                                }
                            } else {
                                proxyPort = -1;
                            }
                        }
                    }
                    if (lock != null) {
                        reconfigLock.unlock();
                        try {
                            logger.finest("Waiting on reconfig lock");
                            lock.get(RECONFIG_LOCK_TIMEOUT_SEC, TimeUnit.SECONDS);
                        } catch (ExecutionException e) {
                            throw new IllegalStateException(e);
                        }
                    } else {
                        GrizzlyFuture future = new GrizzlyFuture();
                        if (isLoggingFinest) {
                            logger.finest("Set reconfig lock for ports: " + port + " and " + proxyPort + ": " + future);
                        }
                        reconfigByPortLock.put(port, future);
                        if (proxyPort != -1) {
                            reconfigByPortLock.put(proxyPort, future);
                        }
                        return new Lock(port, proxyPort);
                    }
                } else {
                    throw new TimeoutException("Lock timeout");
                }
            }
        } finally {
            if (reconfigLock.isHeldByCurrentThread()) {
                reconfigLock.unlock();
            }
        }
    }

    private void releaseListenerLock(Lock lock) {
        final boolean isLoggingFinest = logger.isLoggable(Level.FINEST);
        reconfigLock.lock();
        try {
            final int[] ports = lock.getPorts();
            if (isLoggingFinest) {
                logger.finest("Release reconfig lock for ports: " + Arrays.toString(ports));
            }
            GrizzlyFuture future = null;
            for (int port : ports) {
                if (port != -1) {
                    future = reconfigByPortLock.remove(port);
                }
            }
            if (future != null) {
                if (isLoggingFinest) {
                    logger.finest("Release reconfig lock, set result: " + future);
                }
                future.setResult(new Result<Thread>(Thread.currentThread()));
            }
        } finally {
            reconfigLock.unlock();
        }
    }

    private int getPort(NetworkListener listener) {
        int listenerPort = -1;
        try {
            listenerPort = Integer.parseInt(listener.getPort());
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Can not parse network-listener port number: " + listener.getPort());
            }
        }
        return listenerPort;
    }

    private static final class Lock {
        private final int[] ports;

        public Lock(int... ports) {
            this.ports = ports;
        }

        public int[] getPorts() {
            return ports;
        }
    }
}
