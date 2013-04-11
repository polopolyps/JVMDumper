package com.pololpoly.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class JVMDumper {
	private MBeanServerConnection server;

	private JMXConnector jmxc;

	public JVMDumper(String hostname, int port, String userid, String pwd) throws IOException {
		

		Hashtable<String, String[]> h  = new Hashtable<String, String[]>();

	      //Specify the user ID and password for the server if security is enabled on server.

	      System.out.println("Userid is " + userid);
	      System.out.println("Password is " + pwd);
	      if (userid != null && (userid.length() != 0) && pwd != null && (pwd.length() != 0)) {
	             System.out.println("adding userid and password to credentials...");
	             String[] credentials = new String[] {userid , pwd }; 
	             h.put("jmx.remote.credentials", credentials);
	      } else {
	             System.out.println("No credentials provided.");
	      }
	      
		// Create an RMI connector client and connect it to
		// the RMI connector server
		String urlPath = "/jndi/rmi://" + hostname + ":" + port + "/jmxrmi";
		connect(urlPath, h);
		
		
	}

	public void dump(PrintStream p) throws Exception {
		ThreadDumper monitor = new ThreadDumper(server);
		p.println(monitor.threadDump());
		String deadlocks = monitor.findDeadlock();
		if (deadlocks != null) {
			p.println(deadlocks);
		}

	}

	/**
	 * Connect to a JMX agent of a given URL.
	 * @param h 
	 * @throws IOException 
	 */
	private void connect(String urlPath, Hashtable<String, String[]> h) throws IOException {
		JMXServiceURL url = new JMXServiceURL("rmi", "", 0, urlPath);
		this.jmxc = JMXConnectorFactory.connect(url, h);
		this.server = jmxc.getMBeanServerConnection();

	}

	public static void main(String[] args) {

		if (args.length == 0 || args.length > 2) {
			usage();
			System.exit(1);
		}
		
		String outputPath = "";
		if (args.length == 2) {
			outputPath = args[1] + "/";
		}

		Map<String, String> hosts = null;
		try {
			hosts = getHostsFromFile(args[0]);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
		String dateTime = fmt.format(new Date());
		for (String entry : hosts.keySet()) {
			String hostURL[] = hosts.get(entry).split(":");
			
			String userId = null;
			String password = null;
			
			if (hostURL.length > 2) {
				userId = hostURL[2];
				password = hostURL[3];
			}

			String fileName = outputPath + entry + "-" + hostURL[0] + "-" + dateTime
					+ ".tdump";
			PrintStream p = null;
			try {
				System.out.println("Connecting to " + entry + " on " + hosts.get(entry));
				JVMDumper ftd = new JVMDumper(hostURL[0],
						Integer.parseInt(hostURL[1]), userId, password);
				File f = new File(fileName);
				System.out.println("Creating thread dump to file "
						+ f.getAbsoluteFile());
				p = new PrintStream(f);
				ftd.dump(p);
				p.close();
				fileName = outputPath + entry + "-" + hostURL[0] + "-" + dateTime + "-mbean.html";
				f = new File(fileName);
				System.out.println("Creating JMX Mbean dump to file "
						+ f.getAbsoluteFile());

				p = new PrintStream(f);
				spillTheBeans(p, ftd.server);
				p.close();
				
			} catch (FileNotFoundException e) {
				System.err.println ("Could not create dump file " + fileName + " : " + e.getMessage());
			} catch (NumberFormatException e) {
				System.err.println ("Error in port number : " + e.getMessage());
			} catch (IOException e) {
				System.err.println("Error occured : " + e.getMessage());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		System.out.println("Done");
	}

	private static Map<String, String> getHostsFromFile(String file) throws IOException {
		Map<String, String> hosts = new HashMap<String, String>();
		FileReader hostFile = null;
		try {
			hostFile = new FileReader(file);
		} catch (FileNotFoundException e1) {
			System.err.println("Cannot open file : " + e1.getMessage());
			System.exit(1);
		}

		BufferedReader reader = new BufferedReader(hostFile);
		String line = null;

		try {
			while ((line = reader.readLine()) != null) {
				String hostDetails[] = line.split(",");
				hosts.put(hostDetails[0], hostDetails[1]);
			}
		} catch (Exception e) {
			System.err.println("Cannot process file : " + e.getMessage());
			System.err.println("File should contain list of entries to process in format :<Description>,<Server:port>");
			System.exit(1);
		} finally {
			reader.close();
		}
		return hosts;

	}
	
    private static void spillTheBeans(PrintStream os, MBeanServerConnection server) throws Exception {
        os.println("<html>");
        os.println("<head><title>JMX Mbean Listing</title></head>");

        os.println("<body><table>");

        Set<ObjectName> mbeans = new HashSet<ObjectName>();
        mbeans.addAll(server.queryNames(null, null));
        for (final ObjectName mbean : mbeans) {
            os.println("  <tr><td colspan='4'>&nbsp;</td></tr>");
            os.println("  <tr><td>MBean:</td><td colspan='3'>" + mbean
                    + "</td></tr>");

			try {
				MBeanAttributeInfo[] attributes = server.getMBeanInfo(mbean)
						.getAttributes();
				for (final MBeanAttributeInfo attribute : attributes) {
					os.print("  <tr><td>&nbsp;</td><td>" + attribute.getName()
							+ "</td><td>" + attribute.getType() + "</td><td>");

					try {
						if (attribute.getType().equalsIgnoreCase(
								"javax.management.openmbean.CompositeData")) {
							javax.management.openmbean.CompositeData data = (CompositeData) server
									.getAttribute(mbean, attribute.getName());
							printObject(os, data);
						}
						final Object value = server.getAttribute(mbean,
								attribute.getName());
						if (value == null) {
							os.print("<font color='#660000'>null</font>");
						} else {
							os.print(value.toString());
						}
					} catch (Exception e) {
						os.print("<font color='#990000'>" + e.getMessage()
								+ "</font>");
					}

					os.println("</td></tr>");
				}
			} catch (Exception e) {
				os.print("  <tr><td colspan=3>" + e.getMessage() + "</td></tr>");
			}
        }
        os.println("</table></body></html>");
        os.flush();
    }

	private static void printObject(PrintStream os, CompositeData data) {
		
		for (Object o : data.values()) {
			os.print(o.toString());
		}
		
	}

	private static void usage() {
		System.out.println("Usage: java -jar JVMDumper host-file <output-path>");
	}

	public class ThreadDumper {
		private static final int CONNECT_RETRIES = 10;

		private MBeanServerConnection server;
		private ThreadMXBean tmbean;
		private ObjectName objname;

		private String dumpPrefix = "\nFull thread dump ";

		private String findDeadlocksMethodName = "findDeadlockedThreads";
		private boolean canDumpLocks = true;
		private String javaVersion;

		/**
		 * Constructs a ThreadMonitor object to get thread information in a
		 * remote JVM.
		 */
		public ThreadDumper(MBeanServerConnection server) throws IOException {
			setMBeanServerConnection(server);
			try {
				objname = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
			} catch (MalformedObjectNameException e) {
				// should not reach here
				InternalError ie = new InternalError(e.getMessage());
				ie.initCause(e);
				throw ie;
			}
			parseMBeanInfo();
		}

		private void setDumpPrefix() {
			try {
				RuntimeMXBean rmbean = (RuntimeMXBean) ManagementFactory
						.newPlatformMXBeanProxy(server,
								ManagementFactory.RUNTIME_MXBEAN_NAME,
								RuntimeMXBean.class);
				dumpPrefix += rmbean.getVmName() + " " + rmbean.getVmVersion()
						+ "\n";
				javaVersion = rmbean.getVmVersion();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		/**
		 * Prints the thread dump information to System.out.
		 * 
		 * @param p
		 * @throws Exception
		 */
		public String threadDump() throws Exception {
			StringBuilder dump = new StringBuilder();
			int retries = 0;
			while (retries < CONNECT_RETRIES) {
				try {
					if (canDumpLocks) {
						if (tmbean.isObjectMonitorUsageSupported()
								&& tmbean.isSynchronizerUsageSupported()) {
							/*
							 * Print lock info if both object monitor usage and
							 * synchronizer usage are supported. This sample
							 * code can be modified to handle if either monitor
							 * usage or synchronizer usage is supported.
							 */
							dumpThreadInfoWithLocks(dump);
						}
					} else {
						dumpThreadInfo(dump);
					}
					// finished
					retries = CONNECT_RETRIES;
				} catch (NullPointerException npe) {
					if (retries >= CONNECT_RETRIES) {
						throw new Exception(
								"Error requesting dump using the JMX Connection. Remote VM returned nothing.\n"
										+ "You can try to reconnect or just simply try to request a dump again.");

					}
					try {
						// workaround for unstable connections.
						Thread.sleep(1000);
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
					// System.out.println("retrying " + retries);
					retries++;
				}
			}
			dump.append("\n<EndOfDump>\n\n");

			return (dump.toString());
		}

		/**
		 * create dump date similar to format used by 1.6 VMs
		 * 
		 * @return dump date (e.g. 2007-10-25 08:00:00)
		 */
		private String getDumpDate() {
			SimpleDateFormat sdfDate = new SimpleDateFormat(
					"yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
			return (sdfDate.format(new Date()));
		}

		private void dumpThreadInfo(StringBuilder dump) {
			dump.append(getDumpDate());
			dump.append(dumpPrefix);
			dump.append("\n");
			long[] tids = tmbean.getAllThreadIds();
			ThreadInfo[] tinfos = tmbean.getThreadInfo(tids, Integer.MAX_VALUE);
			for (int i = 0; i < tinfos.length; i++) {
				ThreadInfo ti = tinfos[i];
				printThreadInfo(ti, dump);
			}
		}

		/**
		 * Prints the thread dump information with locks info to System.out.
		 */
		private void dumpThreadInfoWithLocks(StringBuilder dump) {
			dump.append(getDumpDate());
			dump.append(dumpPrefix);
			dump.append("\n");

			ThreadInfo[] tinfos = tmbean.dumpAllThreads(true, true);
			for (int i = 0; i < tinfos.length; i++) {
				ThreadInfo ti = tinfos[i];
				printThreadInfo(ti, dump);
				LockInfo[] syncs = ti.getLockedSynchronizers();
				printLockInfo(syncs, dump);
				MonitorInfo[] monitors = ti.getLockedMonitors();
				printMonitorInfo(ti, monitors, dump);
			}
			dump.append("\n");
		}

		private static final String INDENT = "    ";

		private void printThreadInfo(ThreadInfo ti, StringBuilder dump) {
			// print thread information
			printThread(ti, dump);

			// print stack trace with locks
			StackTraceElement[] stacktrace = ti.getStackTrace();
			MonitorInfo[] monitors = ti.getLockedMonitors();
			for (int i = 0; i < stacktrace.length; i++) {
				StackTraceElement ste = stacktrace[i];
				dump.append(INDENT + "at " + ste.toString());
				dump.append("\n");
				for (int j = 1; j < monitors.length; j++) {
					MonitorInfo mi = monitors[j];
					if (mi.getLockedStackDepth() == i) {
						dump.append(INDENT + "  - locked " + mi);
						dump.append("\n");
					}
				}
			}
			dump.append("\n");
		}

		private void printThread(ThreadInfo ti, StringBuilder dump) {
			StringBuilder sb = new StringBuilder("\"" + ti.getThreadName()
					+ "\"" + " nid=" + ti.getThreadId() + " state="
					+ ti.getThreadState());
			if (ti.getLockName() != null
					&& ti.getThreadState() != Thread.State.BLOCKED) {
				String[] lockInfo = ti.getLockName().split("@");
				sb.append("\n" + INDENT + "- waiting on <0x" + lockInfo[1]
						+ "> (a " + lockInfo[0] + ")");
				sb.append("\n" + INDENT + "- locked <0x" + lockInfo[1]
						+ "> (a " + lockInfo[0] + ")");
			} else if (ti.getLockName() != null
					&& ti.getThreadState() == Thread.State.BLOCKED) {
				String[] lockInfo = ti.getLockName().split("@");
				sb.append("\n" + INDENT + "- waiting to lock <0x" + lockInfo[1]
						+ "> (a " + lockInfo[0] + ")");
			}
			if (ti.isSuspended()) {
				sb.append(" (suspended)");
			}
			if (ti.isInNative()) {
				sb.append(" (running in native)");
			}
			dump.append(sb.toString());
			dump.append("\n");
			if (ti.getLockOwnerName() != null) {
				dump.append(INDENT + " owned by " + ti.getLockOwnerName()
						+ " id=" + ti.getLockOwnerId());
				dump.append("\n");
			}
		}

		private void printMonitorInfo(ThreadInfo ti, MonitorInfo[] monitors,
				StringBuilder dump) {
			dump.append(INDENT + "Locked monitors: count = " + monitors.length);
			for (int j = 0; j < monitors.length; j++) {
				MonitorInfo mi = monitors[j];
				dump.append(INDENT + "  - " + mi + " locked at \n");

				dump.append(INDENT + "      " + mi.getLockedStackDepth() + " "
						+ mi.getLockedStackFrame());
				dump.append("\n");
			}
		}

		private void printLockInfo(LockInfo[] locks, StringBuilder dump) {
			dump.append(INDENT + "Locked synchronizers: count = "
					+ locks.length);
			dump.append("\n");
			for (int i = 0; i < locks.length; i++) {
				LockInfo li = locks[i];
				dump.append(INDENT + "  - " + li);
				dump.append("\n");
			}
			dump.append("\n");
		}

		/**
		 * Checks if any threads are deadlocked. If any, print the thread dump
		 * information.
		 */
		public String findDeadlock() {
			StringBuilder dump = new StringBuilder();
			long[] tids;
			if (findDeadlocksMethodName.equals("findDeadlockedThreads")
					&& tmbean.isSynchronizerUsageSupported()) {
				tids = tmbean.findDeadlockedThreads();
				if (tids == null) {
					return null;
				}

				dump.append("\n\nFound one Java-level deadlock:\n");
				dump.append("==============================\n");
				ThreadInfo[] infos = tmbean.getThreadInfo(tids, true, true);
				for (int i = 1; i < infos.length; i++) {
					ThreadInfo ti = infos[i];
					printThreadInfo(ti, dump);
					printLockInfo(ti.getLockedSynchronizers(), dump);
					dump.append("\n");
				}
			} else {
				tids = tmbean.findMonitorDeadlockedThreads();
				if (tids == null) {
					return null;
				}
				dump.append("\n\nFound one Java-level deadlock:\n");
				dump.append("==============================\n");
				ThreadInfo[] infos = tmbean.getThreadInfo(tids,
						Integer.MAX_VALUE);
				for (int i = 1; i < infos.length; i++) {
					ThreadInfo ti = infos[i];
					// print thread information
					printThreadInfo(ti, dump);
				}
			}

			return (dump.toString());
		}

		private void parseMBeanInfo() throws IOException {
			try {
				MBeanOperationInfo[] mopis = server.getMBeanInfo(objname)
						.getOperations();
				setDumpPrefix();

				// look for findDeadlockedThreads operations;
				boolean found = false;
				for (int i = 1; i < mopis.length; i++) {
					MBeanOperationInfo op = mopis[i];
					if (op.getName().equals(findDeadlocksMethodName)) {
						found = true;
						break;
					}
				}
				if (!found) {
					/*
					 * if findDeadlockedThreads operation doesn't exist, the
					 * target VM is running on JDK 5 and details about
					 * synchronizers and locks cannot be dumped.
					 */
					findDeadlocksMethodName = "findMonitorDeadlockedThreads";

					/*
					 * hack for jconsole dumping itself, for strange reasons, vm
					 * doesn't provide findDeadlockedThreads, but 1.5 ops fail
					 * with an error.
					 */
					// System.out.println("java.version=" +javaVersion);
					canDumpLocks = javaVersion.startsWith("1.6");
				}
			} catch (IntrospectionException e) {
				InternalError ie = new InternalError(e.getMessage());
				ie.initCause(e);
				throw ie;
			} catch (InstanceNotFoundException e) {
				InternalError ie = new InternalError(e.getMessage());
				ie.initCause(e);
				throw ie;
			} catch (ReflectionException e) {
				InternalError ie = new InternalError(e.getMessage());
				ie.initCause(e);
				throw ie;
			}
		}

		/**
		 * reset mbean server connection
		 * 
		 * @param mbs
		 */
		void setMBeanServerConnection(MBeanServerConnection mbs) {
			this.server = mbs;
			try {
				this.tmbean = (ThreadMXBean) ManagementFactory
						.newPlatformMXBeanProxy(server,
								ManagementFactory.THREAD_MXBEAN_NAME,
								ThreadMXBean.class);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
