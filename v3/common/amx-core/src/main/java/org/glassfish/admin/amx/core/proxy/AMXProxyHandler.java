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
package org.glassfish.admin.amx.core.proxy;

import org.glassfish.admin.amx.base.DomainRoot;

import org.glassfish.admin.amx.core.AMXProxy;
import org.glassfish.admin.amx.core.Extra;
import org.glassfish.admin.amx.core.Util;
import static org.glassfish.api.amx.AMXValues.*;

import org.glassfish.admin.amx.core.proxy.ProxyFactory;

import org.glassfish.admin.amx.util.jmx.JMXUtil;
import org.glassfish.admin.amx.util.jmx.MBeanProxyHandler;
import org.glassfish.admin.amx.util.ClassUtil;
import org.glassfish.admin.amx.util.SetUtil;
import org.glassfish.admin.amx.util.ExceptionUtil;
import org.glassfish.admin.amx.util.StringUtil;

import javax.management.*;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import org.glassfish.admin.amx.annotation.Stability;
import org.glassfish.admin.amx.annotation.Taxonomy;
import org.glassfish.admin.amx.base.Tools;
import static org.glassfish.api.amx.AMXValues.*;
import org.glassfish.admin.amx.core.PathnameParser;
import org.glassfish.admin.amx.util.TypeCast;

/**
Extends MBeanProxyHandler by also supporting the functionality required of an AMX.
 */
@Taxonomy(stability = Stability.PRIVATE)
public final class AMXProxyHandler extends MBeanProxyHandler
        implements AMXProxy, Extra
{

    private static void sdebug(final String s)
    {
        System.out.println(s);
    }
    private final ObjectName mParentObjectName;
    private final String mName;

    public <T extends AMXProxy> T as(final Class<T> intf)
    {
        final Class<?> thisClass = this.getClass();

        if (this.getClass().isAssignableFrom(intf))
        {
            return intf.cast(this);
        }

        return proxyFactory().getProxy(getObjectName(), getMBeanInfo(), intf);

    //throw new IllegalArgumentException( "Cannot convert " + getObjectName() +
    // " to interface " + intf.getName() + ", interfaceName from Descriptor = " + interfaceName());
    }

    public Extra extra()
    {
        return this;
    }

    public static AMXProxyHandler unwrap(final AMXProxy proxy)
    {
        return (AMXProxyHandler) Proxy.getInvocationHandler(proxy);
    }

    /**
    Create a new AMX proxy.
     */
    protected AMXProxyHandler(
            final MBeanServerConnection conn,
            final ObjectName objectName,
            final MBeanInfo mbeanInfo)
            throws IOException
    {
        super(conn, objectName, mbeanInfo);

        try
        {
            // one call, so one trip to the server
            final AttributeList attrs = conn.getAttributes(objectName, new String[]
                    {
                        ATTR_NAME, ATTR_PARENT
                    });
            final Map<String, Object> m = JMXUtil.attributeListToValueMap(attrs);

            mParentObjectName = (ObjectName) m.get(ATTR_PARENT);
            mName = (String) m.get(ATTR_NAME);
        }
        catch (final Exception e)
        {
            throw new RuntimeException("Can't get Name and/or Parent attributes from " + objectName, e);
        }
    }
    private static final String GET = "get";
    public final static String ADD_NOTIFICATION_LISTENER = "addNotificationListener";
    public final static String REMOVE_NOTIFICATION_LISTENER = "removeNotificationListener";
    private final static String QUERY = "query";


    public final DomainRoot domainRootProxy()
    {
        return proxyFactory().getDomainRootProxy();
    }
    
    private static final String STRING = String.class.getName();
    private static final String[] EMPTY_SIG = new String[0];
    private static final String[] STRING_SIG = new String[]
    {
        STRING
    };

    protected <T extends AMXProxy> T getProxy(final ObjectName objectName, final Class<T> intf)
    {
        return (proxyFactory().getProxy(objectName, intf));
    }

    protected AMXProxy getProxy(final ObjectName objectName)
    {
        return getProxy(objectName, AMXProxy.class);
    }

    private Object invokeTarget(
            final String methodName,
            final Object[] args,
            final String[] sig)
            throws IOException, ReflectionException, InstanceNotFoundException, MBeanException,
                   AttributeNotFoundException
    {
        final int numArgs = args == null ? 0 : args.length;

        Object result = null;

        if (numArgs == 0 &&
            methodName.startsWith(GET))
        {
            final String attributeName = StringUtil.stripPrefix(methodName, GET);
            result = getMBeanServerConnection().getAttribute(getObjectName(), attributeName);
        }
        else
        {
            result = getMBeanServerConnection().invoke(getObjectName(), methodName, args, sig);
        }

        return result;
    }

    /**
    Return true if the method is one that is requesting a single AMX object.
    Such methods are client-side methods and do not operate on the target MBean.
     */
    protected static boolean isSingleProxyGetter(final Method method, final int argCount)
    {
        boolean isProxyGetter = false;

        final String name = method.getName();
        if ((name.startsWith(GET) || name.startsWith(QUERY)) &&
            AMXProxy.class.isAssignableFrom(method.getReturnType()))
        {
            isProxyGetter = true;
        }

        return (isProxyGetter);
    }

    /**
    The method is one that requests a Proxy. The method could retrieve a real attribute,
    but if there is no real Attribute, attempt to find a child of the matching type.
     */
    AMXProxy invokeSingleProxyGetter(
            final Object myProxy,
            final Method method,
            final Object[] args)
            throws IOException, ReflectionException, InstanceNotFoundException, MBeanException,
                   AttributeNotFoundException
    {
        final String methodName = method.getName();
        final int numArgs = (args == null) ? 0 : args.length;

        final Class<? extends AMXProxy> returnClass = method.getReturnType().asSubclass(AMXProxy.class);
        ObjectName objectName = null;

        if (numArgs == 0)
        {
            //System.out.println( "invokeSingleProxyGetter: intf = " + returnClass.getName() );

            // If a real Attribute exists with this name then it takes priority
            final String attrName = JMXUtil.getAttributeName(method);
            if (getAttributeInfo(attrName) != null)
            {
                objectName = (ObjectName) invokeTarget(methodName, null, null);
            }
            else
            {
                final String type = Util.deduceType(returnClass);

                //System.out.println( "invokeSingleProxyGetter: type = " + type );

                final AMXProxy childProxy = child(type);
                objectName = childProxy == null ? null : childProxy.extra().objectName();
            }
        }
        else
        {
            objectName = (ObjectName) invokeTarget(methodName, args, STRING_SIG);
        }

        return objectName == null ? null : getProxy(objectName, returnClass);
    }

    private static String toString(Object o)
    {
        //String result  = o == null ? "null" : SmartStringifier.toString( o );
        String result = "" + o;

        final int MAX_LENGTH = 256;
        if (result.length() > MAX_LENGTH)
        {
            result = result.substring(0, MAX_LENGTH - 1) + "...";
        }

        return result;
    }

    private static String[] getStringSig(final Method method)
    {
        final Class[] sig = method.getParameterTypes();
        final String[] stringSig = ClassUtil.classnamesFromSignature(sig);
        return (stringSig);
    }
    private static final Map<String, AMXProxy> EMPTY_String_AMX = Collections.emptyMap();
    private final static Class[] NOTIFICATION_LISTENER_SIG1 = new Class[]
    {
        NotificationListener.class
    };
    private final static Class[] NOTIFICATION_LISTENER_SIG2 = new Class[]
    {
        NotificationListener.class,
        NotificationFilter.class,
        Object.class
    };
    /** Cached forever, parent ObjectName */
    private static final String GET_PARENT = GET + ATTR_PARENT;
    /** proxy method */
    private static final String METHOD_NAME_PROP = "nameProp";
    private static final String METHOD_TYPE = "type";
    private static final String METHOD_PARENT_PATH = "parentPath";
    /** proxy method */
    private static final String METHOD_CHILDREN = "children";
    /** proxy method */
    private static final String METHOD_CHILDREN_MAP = "childrenMap";
    /** proxy method */
    private static final String METHOD_CHILDREN_MAPS = "childrenMaps";
    /** proxy method */
    private static final String METHOD_CHILDREN_SET = "childrenSet";
    /** proxy method */
    private static final String METHOD_CHILD = "child";
    /** proxy method */
    private static final String METHOD_PARENT = "parent";
    /** proxy method */
    private static final String METHOD_OBJECTNAME = "objectName";
    /** proxy method */
    private static final String METHOD_EXTRA = "extra";
    /** proxy method */
    private static final String METHOD_AS = "as";
    /** proxy method */
    private static final String METHOD_VALID = "valid";
    /** proxy method */
    private static final String METHOD_ATTRIBUTES_MAP = "attributesMap";
    /** proxy method */
    private static final String METHOD_ATTRIBUTE_NAMES = "attributeNames";
    /** proxy method */
    private static final String METHOD_PATHNAME = "path";

    /**
    These Attributes are handled specially.  For example, J2EE_TYPE and
    J2EE_NAME are part of the ObjectName.
     */
    private static final Set<String> SPECIAL_METHOD_NAMES = SetUtil.newUnmodifiableStringSet(
            GET_PARENT,
            METHOD_NAME_PROP,
            METHOD_TYPE,
            METHOD_PARENT,
            METHOD_PARENT_PATH,
            METHOD_CHILDREN_SET,
            METHOD_CHILDREN_MAP,
            METHOD_CHILDREN_MAPS,
            METHOD_CHILD,
            METHOD_OBJECTNAME,
            METHOD_EXTRA,
            METHOD_AS,
            METHOD_VALID,
            METHOD_ATTRIBUTES_MAP,
            METHOD_ATTRIBUTE_NAMES,
            METHOD_PATHNAME,
            ADD_NOTIFICATION_LISTENER,
            REMOVE_NOTIFICATION_LISTENER);

    /**
    Handle a "special" method; one that requires special handling and/or can
    be dealt with on the client side and/or can be handled most efficiently
    by special-casing it.
     */
    private Object handleSpecialMethod(
            final Object myProxy,
            final Method method,
            final Object[] args)
            throws ClassNotFoundException, JMException, IOException
    {
        final String methodName = method.getName();
        final int numArgs = args == null ? 0 : args.length;
        Object result = null;
        boolean handled = false;

        if (numArgs == 0)
        {
            handled = true;
            if (methodName.equals(METHOD_PARENT))
            {
                result = parent();
            }
            else if (methodName.equals(GET_PARENT))
            {
                result = parent() == null ? null : parent().extra().objectName();
            }
            else if (methodName.equals(METHOD_CHILDREN_SET))
            {
                result = childrenSet();
            }
            else if (methodName.equals(METHOD_CHILDREN_MAPS))
            {
                result = childrenMaps();
            }
            else if (methodName.equals(METHOD_EXTRA))
            {
                result = this;
            }
            else if (methodName.equals(METHOD_OBJECTNAME))
            {
                result = getObjectName();
            }
            else if (methodName.equals(METHOD_NAME_PROP))
            {
                result = getObjectName().getKeyProperty(NAME_KEY);
            }
            else if (methodName.equals(METHOD_TYPE))
            {
                result = type();
            }
            else if (methodName.equals(METHOD_PARENT_PATH))
            {
                result = parentPath();
            }
            else if (methodName.equals(METHOD_ATTRIBUTES_MAP))
            {
                result = attributesMap();
            }
            else if (methodName.equals(METHOD_ATTRIBUTE_NAMES))
            {
                result = attributeNames();
            }
            else if (methodName.equals(METHOD_VALID))
            {
                result = valid();
            }
            else if (methodName.equals(METHOD_PATHNAME))
            {
                result = path();
            }
            else
            {
                handled = false;
            }
        }
        else if (numArgs == 1)
        {
            handled = true;
            final Object arg = args[0];

            if (methodName.equals("equals"))
            {
                result = equals(arg);
            }
            else if (methodName.equals(METHOD_ATTRIBUTES_MAP))
            {
                result = attributesMap( TypeCast.checkedStringSet( Set.class.cast(arg) ) );
            }
            else if (methodName.equals(METHOD_CHILDREN_MAP))
            {
                if (arg instanceof String)
                {
                    result = childrenMap((String) arg);
                }
                else if (arg instanceof Class)
                {
                    result = childrenMap((Class) arg);
                }
                else
                {
                    handled = false;
                }
            }
            else if (methodName.equals(METHOD_CHILD))
            {
                if (arg instanceof String)
                {
                    result = child((String) arg);
                }
                else if (arg instanceof Class)
                {
                    result = child((Class) arg);
                }
                else
                {
                    handled = false;
                }
            }
            else if (methodName.equals(METHOD_AS) && (arg instanceof Class))
            {
                result = as((Class) arg);
            }
            else
            {
                handled = false;
            }
        }
        else
        {
            handled = true;
            final Class[] signature = method.getParameterTypes();

            if (methodName.equals(ADD_NOTIFICATION_LISTENER) &&
                (ClassUtil.sigsEqual(NOTIFICATION_LISTENER_SIG1, signature) ||
                 ClassUtil.sigsEqual(NOTIFICATION_LISTENER_SIG2, signature)))
            {
                addNotificationListener(args);
            }
            else if (methodName.equals(REMOVE_NOTIFICATION_LISTENER) &&
                     (ClassUtil.sigsEqual(NOTIFICATION_LISTENER_SIG1, signature) ||
                      ClassUtil.sigsEqual(NOTIFICATION_LISTENER_SIG2, signature)))
            {
                removeNotificationListener(args);
            }
            else
            {
                handled = false;
            }
        }

        if (!handled)
        {
            assert (false);
            throw new RuntimeException("unknown method: " + method);
        }

        return (result);
    }

    public final Object invoke(
            final Object myProxy,
            final Method method,
            final Object[] args)
            throws java.lang.Throwable
    {
        try
        {
            //System.out.println( "invoking: " + method.getName()  );
            final Object result = _invoke(myProxy, method, args);

            // System.out.println( "invoke: " + method.getName() + ", result = " + result );

            assert (result == null ||
                    ClassUtil.IsPrimitiveClass(method.getReturnType()) ||
                    method.getReturnType().isAssignableFrom(result.getClass())) :
                    method.getName() + ": result of type " + result.getClass().getName() +
                    " not assignable to " + method.getReturnType().getName() + ", " +
                    "interfaces: " + toString(result.getClass().getInterfaces()) +
                    ", ObjectName = " + getObjectName();

            //System.out.println( "invoke: " + method.getName() + ", return result = " + result );
            return result;
        }
        catch (IOException e)
        {
            proxyFactory().checkConnection();
            throw e;
        }
        catch (InstanceNotFoundException e)
        {
            isValid();
            throw e;
        }
    }

    protected Object _invoke(
            final Object myProxy,
            final Method method,
            final Object[] args)
            throws java.lang.Throwable
    {
        debugMethod(method.getName(), args);
        Object result = null;
        final String methodName = method.getName();
        final int numArgs = args == null ? 0 : args.length;
        //System.out.println( "_invoke: " + methodName + " on " + objectName() );

        if (SPECIAL_METHOD_NAMES.contains(methodName))
        {
            result = handleSpecialMethod(myProxy, method, args);
        }
        else
        {
            //System.out.println( "_invoke: (not handled): " + methodName + " on " + objectName() );
            if (isSingleProxyGetter(method, numArgs))
            {
                result = invokeSingleProxyGetter(myProxy, method, args);
            }
            else
            {
                result = super.invoke(myProxy, method, args);
            }
        }

        // AUTO-CONVERT certain return types to proxy from ObjectName, ObjectName[]

        final Class<?> returnType = method.getReturnType();

        if ((result instanceof ObjectName) &&
            AMXProxy.class.isAssignableFrom(returnType))
        {
            result = getProxy((ObjectName) result, returnType.asSubclass(AMXProxy.class));
        }
        else if (result != null &&
                 result instanceof ObjectName[])
        {
            //System.out.println( "_invoke: trying to make ObjectName[] into proxies for " + method.getName() );
            final ObjectName[] items = (ObjectName[]) result;

            Class<? extends AMXProxy> proxyClass = AMXProxy.class;
            if (method.getGenericReturnType() instanceof ParameterizedType)
            {
                proxyClass = getProxyClass((ParameterizedType) method.getGenericReturnType());
            }

            if (Set.class.isAssignableFrom(returnType))
            {
                result = proxyFactory().toProxySet(items, proxyClass);
            }
            else if (List.class.isAssignableFrom(returnType))
            {
                result = proxyFactory().toProxyList(items, proxyClass);
            }
            else if (Map.class.isAssignableFrom(returnType))
            {
                result = proxyFactory().toProxyMap(items, proxyClass);
            }
        }

        //System.out.println( "_invoke: done:  result class is " + result.getClass().getName() );
        return (result);
    }

    private Class<? extends AMXProxy> getProxyClass(final ParameterizedType pt)
    {
        Class<? extends AMXProxy> intf = null;

        final Type[] argTypes = pt.getActualTypeArguments();
        if (argTypes.length >= 1)
        {
            final Type argType = argTypes[argTypes.length - 1];
            if ((argType instanceof Class) && AMXProxy.class.isAssignableFrom((Class) argType))
            {
                intf = ((Class) argType).asSubclass(AMXProxy.class);
            }
        }
        if (intf == null)
        {
            intf = AMXProxy.class;
        }
        return intf;
    }

    protected void addNotificationListener(final Object[] args)
            throws IOException, InstanceNotFoundException
    {
        final NotificationListener listener = (NotificationListener) args[ 0];
        final NotificationFilter filter = (NotificationFilter) (args.length <= 1 ? null : args[ 1]);
        final Object handback = args.length <= 1 ? null : args[ 2];

        getMBeanServerConnection().addNotificationListener(
                getObjectName(), listener, filter, handback);
    }

    protected void removeNotificationListener(final Object[] args)
            throws IOException, InstanceNotFoundException, ListenerNotFoundException
    {
        final NotificationListener listener = (NotificationListener) args[ 0];

        // important:
        // this form removes the same listener registered with different filters and/or handbacks
        if (args.length == 1)
        {
            getMBeanServerConnection().removeNotificationListener(getObjectName(), listener);
        }
        else
        {
            final NotificationFilter filter = (NotificationFilter) args[ 1];
            final Object handback = args[ 2];

            getMBeanServerConnection().removeNotificationListener(
                    getObjectName(), listener, filter, handback);
        }
    }

//-----------------------------------
    public static String interfaceName(final MBeanInfo info)
    {
        final Object value = info.getDescriptor().getFieldValue(DESC_STD_INTERFACE_NAME);
        return (String) value;
    }

    @Override
    public String interfaceName()
    {
        String name = super.interfaceName();
        if (name == null)
        {
            name = AMXProxy.class.getName();
        }

        return name;
    }

    public static String genericInterfaceName(final MBeanInfo info)
    {
        final Object value = info.getDescriptor().getFieldValue(DESC_GENERIC_INTERFACE_NAME);
        return (String) value;
    }
    public String genericInterfaceName()
    {
        return genericInterfaceName(mbeanInfo());
    }

    public Class<? extends AMXProxy>  genericInterface()
    {
        return ProxyFactory.genericInterface(mbeanInfo());
    }

    public boolean valid()
    {
        return isValid();
    }

    public ProxyFactory proxyFactory()
    {
        return (ProxyFactory.getInstance(getMBeanServerConnection()));
    }

    public MBeanServerConnection mbeanServerConnection()
    {
        return getMBeanServerConnection();
    }

    public ObjectName objectName()
    {
        return getObjectName();
    }

    public String nameProp()
    {
        // name as found in the ObjectName
        return Util.getNameProp(getObjectName());
    }

    public String parentPath()
    {
        return getObjectName().getKeyProperty(PARENT_PATH_KEY);
    }

    public String type()
    {
        return Util.getTypeProp(getObjectName());
    }

    public String getName()
    {
        // internal *unquoted* name, but we consider it invariant once fetched
        return mName;
    }

    public ObjectName getParent()
    {
        return mParentObjectName;
    }

    public AMXProxy parent()
    {
        return mParentObjectName == null ? null : proxyFactory().getProxy(mParentObjectName);
    }

    public String path()
    {
        // special case DomainRoot, which has no parent
        if (getParent() == null)
        {
            return DomainRoot.PATH;
        }

        final ObjectName on = getObjectName();
        final String parentPath = Util.getParentPathProp(on);

        final String type = Util.getTypeProp(on);
        return PathnameParser.path(parentPath, type, singleton() ? null : Util.getNameProp(on));
    }

    public ObjectName[] getChildren()
    {
        ObjectName[] objectNames = null;
        try
        {
            objectNames = (ObjectName[]) getAttributeNoThrow(ATTR_CHILDREN);
        }
        catch (final Exception e)
        {
            final Throwable t = ExceptionUtil.getRootCause(e);
            if (!(t instanceof AttributeNotFoundException))
            {
                throw new RuntimeException("Could not get Children attribute", e);
            }
        }
        return objectNames;
    }

    /**
    Returns an array of children, including an empty array if there are none, but children
    are possible.  Returns null if children are not possible.
     */
    public Set<AMXProxy> childrenSet()
    {
        return childrenSet(getChildren());
    }

    public Set<AMXProxy> childrenSet(final ObjectName[] objectNames)
    {
        return objectNames == null ? null : SetUtil.newSet(proxyFactory().toProxy(objectNames));
    }

    public Set<String> childrenTypes(final ObjectName[] objectNames)
    {
        final Set<String> types = new HashSet<String>();
        for (final ObjectName o : objectNames)
        {
            final String type = Util.getTypeProp(o);
            types.add(type);
        }
        return types;
    }

    public Map<String, AMXProxy> childrenMap(final String type)
    {
        return childrenMap(type, AMXProxy.class);
    }

    public <T extends AMXProxy> Map<String, T> childrenMap(final Class<T> intf)
    {
        if (!intf.isInterface())
        {
            throw new IllegalArgumentException("" + intf);
        }
        return childrenMap(Util.deduceType(intf), intf);
    }

    public <T extends AMXProxy> Map<String, T> childrenMap(final String type, final Class<T> intf)
    {
        final ObjectName[] objectNames = getChildren();
        if (objectNames == null)
        {
            return null;
        }

        final Map<String, T> m = new HashMap<String, T>();

        for (final ObjectName objectName : objectNames)
        {
            if (Util.getTypeProp(objectName).equals(type))
            {
                m.put(Util.getNameProp(objectName), getProxy(objectName, intf));
            }
        }
        return m;
    }

    public Map<String, Map<String, AMXProxy>> childrenMaps()
    {
        final ObjectName[] children = getChildren();
        if (children == null)
        {
            return null;
        }

        final Set<AMXProxy> childrenSet = childrenSet(children);

        final Map<String, Map<String, AMXProxy>> maps = new HashMap<String, Map<String, AMXProxy>>();
        final Set<String> types = childrenTypes(children);
        for (final String type : types)
        {
            maps.put(type, new HashMap<String, AMXProxy>());
        }

        for (final AMXProxy proxy : childrenSet)
        {
            final Map<String, AMXProxy> m = maps.get( proxy.type() );
            m.put(proxy.nameProp(), proxy);
        }
        return maps;
    }

    public <T extends AMXProxy> Set<T> childrenSet(final String type, final Class<T> intf)
    {
        final Map<String, T> m = childrenMap(type, intf);
        return m == null ? null : new HashSet<T>(m.values());
    }

    public AMXProxy child(final String type)
    {
        return child(type, AMXProxy.class);
    }

    public <T extends AMXProxy> T child(final Class<T> intf)
    {
        final String type = Util.deduceType(intf);
        //sdebug( "Deduced type of " + intf.getName() + " = " + type );
        return child(type, intf);
    }

    public <T extends AMXProxy> T child(final String type, final Class<T> intf)
    {
        //sdebug( "Child " + type + " has interface " + intf.getName() );
        final Map<String, T> children = childrenMap(type, intf);
        if (children.size() == 0)
        {
            return null;
        }
        if (children.size() > 1)
        {
            throw new IllegalArgumentException("Not a singleton: " + type);
        }

        final T child = children.values().iterator().next();
        if (!child.extra().singleton())
        {
            throw new IllegalArgumentException("Not a singleton: " + type);
        }

        return child;
    }

    public <T extends AMXProxy> T child(final String type, final String name, final Class<T> intf)
    {
        final Set<AMXProxy> children = childrenSet();
        if (children == null)
        {
            return null;
        }

        T child = null;
        for (final AMXProxy c : children)
        {
            final ObjectName objectName = c.extra().objectName();
            if (Util.getTypeProp(objectName).equals(type) && Util.getNameProp(objectName).equals(name))
            {
                child = c.as(intf);
                break;
            }
        }
        return child;
    }

    public final MBeanInfo mbeanInfo()
    {
        return getMBeanInfo();
    }
    
    
    public Map<String, Object> attributesMap( final Set<String> attrNames )
    {
        try
        {
            final String[] namesArray = attrNames.toArray(new String[attrNames.size()]);
            final AttributeList attrs = getAttributes(namesArray);
            return JMXUtil.attributeListToValueMap(attrs);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
    public Map<String, Object> attributesMap()
    {
        return attributesMap( attributeNames() );
    }

    public MBeanAttributeInfo getAttributeInfo(final String name)
    {
        for (final MBeanAttributeInfo attrInfo : getMBeanInfo().getAttributes())
        {
            if (attrInfo.getName().equals(name))
            {
                return attrInfo;
            }
        }
        return null;
    }

    public Set<String> attributeNames()
    {
        final String[] names = JMXUtil.getAttributeNames(getMBeanInfo().getAttributes());

        return SetUtil.newStringSet(names);
    }

    public static <T> T getDescriptorField(final MBeanInfo info, final String name, final T defaultValue)
    {
        T value = (T) info.getDescriptor().getFieldValue(name);
        if (value == null)
        {
            value = defaultValue;
        }
        return value;
    }

    public static boolean singleton(final MBeanInfo info)
    {
        return getDescriptorField(info, DESC_IS_SINGLETON, Boolean.FALSE);
    }

    protected <T> T getDescriptorField(final String name, final T defaultValue)
    {
        return getDescriptorField(getMBeanInfo(), name, defaultValue);
    }

    public boolean singleton()
    {
        return getDescriptorField(DESC_IS_SINGLETON, Boolean.FALSE);
    }

    public String group()
    {
        return getDescriptorField(DESC_GROUP, GROUP_OTHER);
    }

    public boolean supportsAdoption()
    {
        return getDescriptorField(DESC_SUPPORTS_ADOPTION, Boolean.FALSE);
    }
    private static final String[] EMPTY_STRINGS = new String[0];

    public String[] subTypes()
    {
        return getDescriptorField(DESC_SUB_TYPES, EMPTY_STRINGS);
    }
    
    public String java() {
        final Tools tools  = domainRootProxy().getTools();
        return tools.java( getObjectName() );
    }
    
    public Descriptor descriptor() {
        return getMBeanInfo().getDescriptor();
    }
    
    public MBeanAttributeInfo attributeInfo(final String attrName) {
        for( final MBeanAttributeInfo info: getMBeanInfo().getAttributes() )
        {
            if ( info.getName().equals(attrName) )
            {
                return info;
            }
        }
        return null;
    }
    
    public MBeanOperationInfo operationInfo(final String operationName) {
        for( final MBeanOperationInfo info: getMBeanInfo().getOperations() )
        {
            if ( info.getName().equals(operationName) )
            {
                return info;
            }
        }
        return null;
    }
}





