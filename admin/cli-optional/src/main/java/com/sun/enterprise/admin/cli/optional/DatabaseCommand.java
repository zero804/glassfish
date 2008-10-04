/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2008 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.enterprise.admin.cli.optional;

import com.sun.enterprise.admin.cli.ClassPathBuilder;
import com.sun.enterprise.cli.framework.CLILogger;
import com.sun.enterprise.cli.framework.CommandException;
import com.sun.enterprise.cli.framework.CommandValidationException;
import static com.sun.enterprise.universal.glassfish.SystemPropertyConstants.*;
import com.sun.enterprise.util.OS;
import java.io.File;
import java.io.FileFilter;

/**
 *  This is an abstract class to be inherited by
 *  StartDatabaseCommand and StopDatabaseCommand.
 *  This classes prepares the variables that is used to
 *  to invoke DerbyControl.  It also contains a pingDatabase
 *  method that is used by both start/stop database command.
 *  @author <a href="mailto:jane.young@sun.com">Jane Young</a> 
 *  @version  $Revision: 1.5 $
 */
// TODO: If you were the one who wrote this code, please come talk to me - Kohsuke
public abstract class DatabaseCommand extends S1ASCommand
{
    private final static String DB_HOST       = "dbhost";
    private final static String DB_PORT       = "dbport";
    private static final String[] MODULES_IN_CLASSPATH = {"glassfish","admin-cli","cli-framework","common-util"};

    protected String dbHost;
    protected String dbPort;
    protected File dbLocation;
    protected File sJavaHome;
    protected File sInstallRoot;
    protected File installModules;
    protected final ClassPathBuilder sClasspath = new ClassPathBuilder();
    protected final ClassPathBuilder sDatabaseClasspath = new ClassPathBuilder();

    /**
     * Prepare variables to invoke start/ping database command
     * DatabaseCommand.
     */
    protected void prepareProcessExecutor() throws Exception
    {
        sInstallRoot = new File(getSystemProperty(INSTALL_ROOT_PROPERTY));
        dbHost = getOption(DB_HOST);
        dbPort = getOption(DB_PORT);
	checkIfPortIsValid(dbPort);
        sJavaHome = new File(getSystemProperty(JAVA_ROOT_PROPERTY));
        installModules = new File(sInstallRoot,"modules");
        dbLocation = new File(getSystemProperty(DERBY_ROOT_PROPERTY));
        checkIfDbInstalled(dbLocation);
        
	sClasspath.addAll(installModules,new FileFilter() {
            public boolean accept(File f) {
                String n = f.getName();
                for (String prefix : MODULES_IN_CLASSPATH) {
                    if(n.startsWith(prefix) && n.endsWith(".jar"))
                        return true;
                }
                return false;
            }
        });

        sDatabaseClasspath
                .add(dbLocation,"lib","derby.jar")
                .add(dbLocation,"lib","derbytools.jar")
                .add(dbLocation,"lib","derbynet.jar")
                .add(dbLocation,"lib","derbyclient.jar");
    }


  /** check if database port is valid.
   *  Derby does not check this so need to add code to check the port number.
   */
    private void checkIfPortIsValid(final String port) throws CommandValidationException
    {
        try
        {
            Integer.parseInt(port);
        } 
        catch(Exception e)
       {
            throw new CommandValidationException(getLocalizedString("InvalidPortNumber", new Object[] {port}));
        }
    }
    
  /** check if database is installed.
   */
    private void checkIfDbInstalled(final File dblocation) throws CommandException
    {
        if (!dblocation.exists()) {
            CLILogger.getInstance().printMessage(getLocalizedString(
                                                     "DatabaseNotInstalled",
                                                     new Object[]{dblocation}));
            throw new CommandException("dblocation not found: " + dblocation);
        } else {
            File derbyJar = new File(new File(dbLocation,"lib"),"derbyclient.jar");
            if (!derbyJar.exists()) {
                CLILogger.getInstance().printMessage(getLocalizedString(
                                                     "DatabaseNotInstalled",
                                                     new Object[]{dblocation}));
                throw new CommandException("derbyclient.jar not found in " + dblocation);
            }
        }
        
    }


    /**
       defines the command to ping the derby database
       Note that when using Darwin (Mac), the property,
       "-Dderby.storage.fileSyncTransactionLog=True" is defined.
    */
    protected String[] pingDatabaseCmd(boolean bRedirect) throws Exception
    {
        if (OS.isDarwin()) {
            return new String[]{getJavaExe().toString(),
                    "-Djava.library.path=" + sInstallRoot + File.separator +
                    "lib", "-Dderby.storage.fileSyncTransactionLog=True", "-cp",
                    sClasspath + File.pathSeparator + sDatabaseClasspath,
                    "com.sun.enterprise.admin.cli.optional.DerbyControl", "ping",
                    dbHost, dbPort, Boolean.valueOf(bRedirect).toString()};
        }
        else {
            return new String[]{getJavaExe().toString(),
                    "-Djava.library.path=" + sInstallRoot + File.separator +
                    "lib", "-cp",
                    sClasspath + File.pathSeparator + sDatabaseClasspath,
                    "com.sun.enterprise.admin.cli.optional.DerbyControl", "ping",
                    dbHost, dbPort, Boolean.valueOf(bRedirect).toString()};
        }
    }

    /**
     * Computes the java executable location from {@link #sJavaHome}.
     */
    protected final File getJavaExe() {
        return new File(new File(sJavaHome,"bin"),"java");
    }
}
