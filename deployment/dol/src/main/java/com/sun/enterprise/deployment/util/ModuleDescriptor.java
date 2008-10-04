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

package com.sun.enterprise.deployment.util;

import com.sun.enterprise.deployment.DeploymentExtensionDescriptor;
import com.sun.enterprise.deployment.Descriptor;
import com.sun.enterprise.deployment.RootDeploymentDescriptor;

import javax.enterprise.deploy.shared.ModuleType;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;
import java.util.jar.Manifest;

/**
 * This class describes a module information for an applicaiton module
 *
 * @author  Jerome Dochez
 * @version 
 */
public class ModuleDescriptor<T extends RootDeploymentDescriptor> extends Descriptor {

    /**
     * type of the module, currently EJB, WEB...
     */
    private ModuleType type;
    
    /**
     * path for the module bundle
     */
    private String path;
    
    /** 
     * alternate descriptor (if any) path
     */
    private String altDD;
    
    /**
     * context-root if dealing with a web module
     */
    private String contextRoot;
    
    /**
     * loaded deployment descriptor for this module
     */
    private T descriptor;
    
    /**
     * manifest information for this module
     */
    private transient Manifest manifest;
    
    /**
     * is it a standalone module, or part of a J2EE Application
     */
    private boolean standalone=false;
    
    /** Creates new ModuleDescriptor */
    public ModuleDescriptor() {
    }

    public void setModuleType(ModuleType type) {
        this.type = type;
    }
    
    
    /** 
     * @return the module type for this module
     */
    public ModuleType getModuleType() {
        if (descriptor!=null) {
            return descriptor.getModuleType();
        } 
        return type;
    }
    
    /**
     * Sets the archive uri as defined in the application xml
     * or the full archive path for standalone modules
     * @path the module path
     */
    public void setArchiveUri(String path) {
        this.path = path;
    }
    
    /**
     * @return the archive uri for this module
     */
    public String getArchiveUri() {
        return path;
    }
    
    /**
     * Sets the path to the alternate deployment descriptors
     * in the application archive
     * @param altDD the uri for the deployment descriptors
     */
    public void setAlternateDescriptor(String altDD) {
        this.altDD = altDD;
    }
    
    /**
     * @return the alternate deployment descriptor path 
     * or null if this module does not use alternate
     * deployment descriptors
     */
    public String getAlternateDescriptor() {
        return altDD;
    }
    
    /**
     * Sets the @see BundleDescriptor descriptor for this 
     * module
     * @param descriptor the module descriptor
     */
    public void setDescriptor(T descriptor) {
        descriptor.setModuleDescriptor(this);
        this.descriptor = descriptor;
    }
    
    /**
     * @return the @see BundleDescriptor module descriptor
     */
    public T getDescriptor() {
        return descriptor;
    } 
    
    /** 
     * Sets the context root for Web module 
     * @param contextRoot the contextRoot
     */
    public void setContextRoot(String contextRoot) {
        this.contextRoot = contextRoot;
    } 
    
    /**
     * @return the context root for a web module
     */
    public String getContextRoot() {
        return contextRoot;
    }
    
    /**
     * @return the @see Manifest manifest information 
     * for this module
     */
    public Manifest getManifest() {
        return manifest;
    }
    
    /**
     * Sets the @see Manifest manifest information for this
     * module
     */
    public void setManifest(Manifest m) {
        manifest = m;
    }
    
    /**
     * @return true if this module is a standalone module
     */
    public boolean isStandalone() {
        return standalone;
    }
    
    /**
     * Sets the standalone flag
     */
    public void setStandalone(boolean standalone) {
        this.standalone = standalone;
    }
    
    /**
     * Add a new deployment-extension for this descriptor
     * @param de deployment-extension descriptor to add
     */
    public void addWebDeploymentExtension(DeploymentExtensionDescriptor de) {
        Vector extensions = (Vector) getExtraAttribute("web-deployment-extension");
        if (extensions==null) {
            extensions = new Vector();
            addExtraAttribute("web-deployment-extension", extensions);
        }
        extensions.add(de);
    }
    
    /**
     * @return an iterator on the deployment-extension
     */
    public Iterator getWebDeploymentExtensions() {
        Vector extensions = (Vector) getExtraAttribute("web-deployment-extension");
        if (extensions!=null) {
            return extensions.iterator();
        } 
        return null;
    }    
    
    
    /** 
     * @return a meaningful string about myself
     */
    public void print(StringBuffer toStringBuffer) {
        toStringBuffer.append(type + " ModuleDescriptor: [  " + path + " ] , altDD = " + altDD);
        if (contextRoot!=null) {
            toStringBuffer.append(" , ContextRoot = " + contextRoot);
        }
    }
    
    
    /**
     * Implementation of the serializable interface since ModuleType is not
     * serializable
     */
    private void writeObject(java.io.ObjectOutputStream out)
     throws IOException {
                 
         // just write this intance fields...
         out.writeObject(path);
         out.writeObject(altDD);
         out.writeObject(contextRoot);
         out.writeObject(descriptor);
         out.writeBoolean(standalone);         
    }
    
    private void readObject(java.io.ObjectInputStream in)
     throws IOException, ClassNotFoundException {
                
         // just read this intance fields...
         path = (String) in.readObject();
         altDD = (String) in.readObject();
         contextRoot = (String) in.readObject();
         descriptor = (T) in.readObject();
         standalone = in.readBoolean();           
    }
}
