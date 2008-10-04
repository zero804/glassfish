/*
 * The contents of this file are subject to the terms 
 * of the Common Development and Distribution License 
 * (the License).  You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at 
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing 
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL 
 * Header Notice in each file and include the License file 
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.  
 * If applicable, add the following below the CDDL Header, 
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.enterprise.admin.jmx.remote.notification;

import javax.management.*;

/**
 * This class is a composite of a NotificationListener, NotificationFilter and a handback object.
 * The listener, filter and handback objects that are being registered by the client
 * via a call to addNotificationListener are set into a new ListenerInfo object.
 * This ListenerInfo object is then registered in the NotificationManagers.
 */
public class ListenerInfo {
    public Object proxy = null;
    public NotificationListener listener = null;
    public NotificationFilter filter = null;
    public Object handback = null;
    public String id = null;

    public ListenerInfo() {
    }

    public ListenerInfo(NotificationListener listener, NotificationFilter filter, Object handback) {
        this.listener = listener;
        this.filter = filter;
        this.handback = handback;
        id = computeId();
    }

    /**
     * Compute an id for this object.
     * The id generated by this method is used to match a listener, filter, handback combination
     * both on the server-side and client-side
     */
    public String computeId() {
        String listenerCode = "null";
        if (listener != null)
            listenerCode = Integer.toString(listener.hashCode());
        String filterCode = "null";
        if (filter != null)
            filterCode = Integer.toString(filter.hashCode());
        String handbackCode = "null";
        if (handback != null)
            handbackCode = Integer.toString(handback.hashCode());
        return  ( listenerCode + ":" + filterCode + ":" + handbackCode );
    }
}

