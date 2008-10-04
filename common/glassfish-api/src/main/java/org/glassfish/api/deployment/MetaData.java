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
package org.glassfish.api.deployment;

import com.sun.enterprise.module.ModuleDefinition;

/**
 * MetaData associated with a Deployer. This is used by the deployment layers
 * to identify the special requirements of the Deployer.
 *
 * Supported Requirements :
 *      invalidatesClassLoader  Deployer can load classes that need to be reloaded
 *                              for the application to run successfully hence requiring
 *                              the class loader to be flushed and reinitialized between
 *                              the prepare and load phase.
 *      componentAPIs           Components can use APIs that are defined outside of the
 *                              component's bundle. These component's APIs (eg. Java EE
 *                              APIs) must be imported by the application class loader
 *                              before any application code is loaded.
 */
public class MetaData {

    final static Class[] empty = new Class[0];

    private final boolean invalidatesCL;
    private final Class[] requires;
    private final Class[] provides;

    /**
     * Constructor for the Deployer's metadata
     *
     * @param invalidatesClassLoader If true, invalidates the class loader used during
     * the deployment's prepare phase
     *
     */
    public MetaData(boolean invalidatesClassLoader, Class[] provides, Class[] requires) {
        this.invalidatesCL = invalidatesClassLoader;
        this.provides = provides;
        this.requires = requires;
    }

    /**
     * Returns whether or not the class loader is invalidated by the Deployer's propare
     * phase.
     * 
     * @return true if the class loader is invalid after the Deployer's prepare phase
     * call.
     */
    public boolean invalidatesClassLoader() {
        return invalidatesCL;
    }

    /**
     * Returns the list of types of metadata this deployer will provide to the deployement
     * context upon the successful completion of the prepare method.
     *
     * @return list of metadata type;
     */
    public Class[] provides() {
        if (provides==null) {
            return empty;
        }
        return provides;
    };                 

    /**
     * Returns the list of types of metadata this deployer will require to run successfully
     * the prepare method.
     *
     * @return list of metadata required type;
     */
    public Class[] requires() {
        if (requires==null) {
            return empty;
        }
        return requires;
    }
}
