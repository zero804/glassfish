/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
package org.glassfish.admin.amxtest.config;

import com.sun.appserv.management.base.Container;
import com.sun.appserv.management.base.XTypes;
import com.sun.appserv.management.config.AMXConfig;
import com.sun.appserv.management.config.ConnectorConnectionPoolConfig;
import com.sun.appserv.management.config.ConnectorConnectionPoolConfigKeys;
import com.sun.appserv.management.config.DomainConfig;
import com.sun.appserv.management.util.misc.MapUtil;

import java.util.Map;

/**
 */
public final class ConnectorConnectionPoolConfigTest
        extends ConfigMgrTestBase {
    private static final String CONNECTOR_DEF_NAME = "javax.resource.cci.ConnectionFactory";
    private static final String RESOURCE_ADAPTOR_NAME = "cciblackbox-tx";
    private static final Map<String, String> OPTIONS = MapUtil.newMap(
            ConnectorConnectionPoolConfigKeys.IGNORE_MISSING_REFERENCES_KEY, "true");

    public ConnectorConnectionPoolConfigTest() {
        if (checkNotOffline("ensureDefaultInstance")) {
            ensureDefaultInstance(getDomainConfig());
        }
    }

    public static String
    getDefaultInstanceName() {
        return getDefaultInstanceName("ConnectorConnectionPoolConfig");
    }

    public static ConnectorConnectionPoolConfig
    ensureDefaultInstance(final DomainConfig dc) {
        ConnectorConnectionPoolConfig result =
                dc.getResourcesConfig().getConnectorConnectionPoolConfigMap().get(getDefaultInstanceName());

        if (result == null) {
            result = createInstance(dc, getDefaultInstanceName(),
                                    ResourceAdapterConfigTest.ensureDefaultInstance(dc).getName(),
                                    CONNECTOR_DEF_NAME, OPTIONS);
        }

        return result;
    }

    public static ConnectorConnectionPoolConfig
    createInstance(
            final DomainConfig dc,
            final String name,
            final String resourceAdapterName,
            final String connectorDefinitionName,
            Map<String, String> optional) {
        return dc.getResourcesConfig().createConnectorConnectionPoolConfig(name,
                                                      connectorDefinitionName, resourceAdapterName, optional);
    }

    protected Container
    getProgenyContainer() {
        return getDomainConfig();
    }

    protected String
    getProgenyJ2EEType() {
        return XTypes.CONNECTOR_CONNECTION_POOL_CONFIG;
    }

    protected void
    removeProgeny(final String name) {
        getDomainConfig().getResourcesConfig().removeConnectorConnectionPoolConfig(name);
    }

    protected final AMXConfig
    createProgeny(
            final String name,
            final Map<String, String> options) {
        final Map<String, String> allOptions = MapUtil.newMap(OPTIONS, options);

        final ConnectorConnectionPoolConfig config =
                getDomainConfig().getResourcesConfig().createConnectorConnectionPoolConfig(
                        name,
                        RESOURCE_ADAPTOR_NAME,
                        CONNECTOR_DEF_NAME, allOptions);
        return (config);
    }
}


