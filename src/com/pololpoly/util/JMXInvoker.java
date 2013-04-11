package com.pololpoly.util;

/*
 *  (c) 2000-2009 Polopoly AB (publ).
 *  This software is protected by copyright law and international copyright
 *  treaties as well as other intellectual property laws and treaties.
 *  All title and rights in and to this software and any copies thereof
 *  are the sole property of Polopoly AB (publ).
 *  Polopoly is a registered trademark of Polopoly AB (publ).
 *
 *  $Id:$
 */
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
/**
   JMX Command invoker.
   
   <p>Use to list MBeans, list and  get MBean attributes and invoke commands.</p>
   <b>JMXInvoker [USER:PWD@]HOST:PORT|SERVICEURL list|attr|inv [mbeanName method]</b>
   <h2>Examples</h2>
   <p>Host and port listing all mbeans:</p>
   <pre>
   JMXInvoker my.host.com:8181 list
   </pre>
   <p>Host and port, with user and password listing all attributes of an
   mbean:</p>
   <pre>
   JMXInvoker monotorRole:MYPASS@my.host.com:8181 list com.polopoly:host=my,application=indexserver,module=pear,name=SystemInfo
   </pre>
   <p>Service URL getting value from an attribute of an mbean:</p>
   <pre>
   JMXInvoker service:jmx:rmi://my.host.com:1112/jndi/rmi://my.host.com:1199/mbeanserver_indexserver  attr com.polopoly:host=my,application=indexserver,module=pear,name=SystemInfo FreeMemory
   </pre>
   <p>Service URL with user/password getting invoking an operation of an mbean:</p>
   <pre>
   JMXInvoker controlRole:MYPASS@service:jmx:rmi:///jndi/rmi://my.host.com:1199/jmxrmi  inv com.polopoly:host=my,application=indexserver,module=pear,name=SystemInfo allStackTracesHTML
   </pre>
 
 * @author <a href="mailto:pra@polopoly.com">Peter Antman</a>
 * @version 1.0
 */

public class JMXInvoker{
    private static String PROTO = "rmi";
    private static String REMOTE_NAME = "jmxrmi";
    
    JMXServiceURL _url;
    String[] _cred;
    MBeanServerConnection _server;
    
    public JMXInvoker(String urlSpec) throws MalformedURLException {
	String urlString = urlSpec;

	int at = urlSpec.indexOf("@");
	if (at > -1) {
	    String up = urlSpec.substring(0, at);
	    _cred = up.split(":");
	    urlString = urlSpec.
		substring(at + 1, urlSpec.length());
	}
	
	
	if (!urlString.startsWith("service:")) {
	    urlString = "service:jmx:rmi:///jndi/" + PROTO + "://" +
		urlString + "/" + REMOTE_NAME;
	} 
	_url = new JMXServiceURL(urlString);
    }
    
    public JMXServiceURL getUrl() {
	return _url;
    }
    
    public String[] getCred() {
	return _cred;
    }
    
    public Map<String, Object> getContextProperties() {
	Map<String, Object> contextProperties = 
	    new HashMap<String, Object>();
	contextProperties.put(JMXConnector.CREDENTIALS, getCred());
	return contextProperties;
    }
    
    public void start() throws  java.io.IOException {
	// Contact MBean server
	JMXConnector serverConnector = JMXConnectorFactory.
	    newJMXConnector(getUrl(), getContextProperties());
	serverConnector.connect(getContextProperties());
	_server = serverConnector.getMBeanServerConnection();
    }
    
    public void stop() {
	// NOOP ;-)
    }

    public MBeanServerConnection getServer() {
	return _server;
    }
    

    
    public static void main(String[] args) {
    	
        try {
            
            if (args.length < 2) {
                System.out.println("JMXInvoker [USER:PWD@]HOST:PORT|SERVICEURL list|attr|inv [mbeanName method]");
                System.exit(0);
            }
            
            
            String url = args[0];
            String cmd = args[1];
	    ObjectName mbeanName = null;
            String method = null;
	    
            if (args.length >= 3) {
		mbeanName = new ObjectName(args[2]);
            }
            if (args.length == 4) {
                method = args[3];
            }
	    
	    JMXInvoker inv = new JMXInvoker(url);
	    inv.start();

	    MBeanServerConnection server = inv.getServer();

            // List all Mbeans in server
            if ("list".equals(cmd)) {
                if (mbeanName == null) {
                    // List all mbeans
                    Iterator names = server.queryNames(null,null).iterator();
                    while (names.hasNext()) {
                        System.out.println("MBean ObjectName: "+ names.next());
                    }  
                }
                else {
                    // List attributes
                    MBeanInfo info = server.getMBeanInfo(mbeanName);
                    MBeanAttributeInfo[] attrs = info.getAttributes();
                    System.out.println(mbeanName+":");
                    if (attrs != null) {
                        System.out.println("\tAttributes:");
                        for (int i = 0;i < attrs.length;i++) {
                            System.out.println("\t\t"+attrs[i].getName());
                        }
                        
                    }
                    
                }
                
                
            // Show attribute value
            } else if ("attr".equals(cmd) && method != null) {
                Object o = server.getAttribute(mbeanName, method);
                // Handle type conversion if needed
                System.out.println("Value of attribute "+method+"="+o);
                
            // Invoke method
            } else if ("inv".equals(cmd) && method != null) {
                Object ret = server.invoke(mbeanName, 
                                           method, // Method name
                                           new Object[]{}, // Params
                                           new String[]{} // Method sig
                                           );
                // Handle type conversion if needed
                System.out.println("Return of method "+method+"="+ret);
            }
            
  
        } catch (Exception e) {
            if (e instanceof javax.management.RuntimeOperationsException) {
                javax.management.RuntimeOperationsException r = (javax.management.RuntimeOperationsException)e;
                r.getCause().printStackTrace();
                r.getTargetException().printStackTrace();
            }
            e.printStackTrace();
        }
    }
} // JMXInvoker
