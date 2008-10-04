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
package com.sun.appserv.management.j2ee;

import com.sun.appserv.management.ext.wsmgmt.MessageTrace;

/**
 * The extension to the JSR 77 hierarchy for a WebService Endpoint.  
 * 
 * @since AppServer 9.0
 */
public interface WebServiceEndpoint extends J2EEManagedObject
{
    public final static String J2EE_TYPE = J2EETypes.WEB_SERVICE_ENDPOINT;

    /**
     * Implementation {@link Servlet} or {@link EJB} JSR77 mbean is returned.
     *
     * @return Implementation {@link Servlet} or {@link EJB} JSR77 mbean 
     */
    //public J2EEManagedObject getImplementationPeer();

    /**
     * This returns the underlying implementation Servlet or EJB' type
     * 
     * @return either SERVLET or EJB
     */
    public String getImplementationType();


    /**
     * Returns last N message content and info collected for this web service.
     * WILL CHANGE: actual return type will be CompositeData OR Map
     *
     * @return Map containers keys from {@link MessageTrace}
     */
    public MessageTrace[] getMessagesInHistory();

    /**
     * Reset all the statistics
     */
    public void resetStats();

    /**
     * Returns last reset timestamp in milliseconds.
     *
     * @return  last reset timestamp in milliseconds
     */
    public long getLastResetTime();

}
