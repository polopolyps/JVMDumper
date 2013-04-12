package com.pololpoly.util;

import java.io.IOException;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

public class ThreadDumper {

	private static final String FIND_MONITOR_DEADLOCKED_THREADS = "findMonitorDeadlockedThreads";
	private static final String FIND_DEADLOCKED_THREADS = "findDeadlockedThreads";
	private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
	private static final String INDENT = "    ";
	private static final int CONNECT_RETRIES = 10;

	private MBeanServerConnection server;
	private ThreadMXBean threadMbean;
	private ObjectName objname;

	private String dumpPrefix = "\nFull thread dump ";

	private String findDeadlocksMethodName = FIND_DEADLOCKED_THREADS;
	private boolean canDumpLocks = true;
	private String javaVersion;

	/**
	 * Constructs a ThreadMonitor object to get thread information in a remote
	 * JVM.
	 * @throws DumpException 
	 */
	public ThreadDumper(MBeanServerConnection server) throws IOException, DumpException {
		setMBeanServerConnection(server);
		try {
			objname = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
		} catch (MalformedObjectNameException e) {
			throw new IOException(e);
		}
		parseMBeanInfo();
	}

	private void setDumpPrefix() {
		try {
			RuntimeMXBean rmbean = (RuntimeMXBean) ManagementFactory.newPlatformMXBeanProxy(server,
					ManagementFactory.RUNTIME_MXBEAN_NAME, RuntimeMXBean.class);

			dumpPrefix = "\nFull thread dump " + rmbean.getVmName() + " " + rmbean.getVmVersion() + "\n";
			javaVersion = rmbean.getVmVersion();

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public String threadDump() throws DumpException {
		StringBuilder dump = new StringBuilder();
		int retries = 0;

		while (retries < CONNECT_RETRIES) {
			try {
				if (canDumpLocks) {
					if (threadMbean.isObjectMonitorUsageSupported() && threadMbean.isSynchronizerUsageSupported()) {
						/*
						 * Print lock info if both object monitor usage and
						 * synchronizer usage are supported. This sample code
						 * can be modified to handle if either monitor usage or
						 * synchronizer usage is supported.
						 */
						dump.append(dumpThreadInfoWithLocks());
					}
				} else {
					dump.append(dumpThreadInfo());
				}
				retries = CONNECT_RETRIES;
			} catch (NullPointerException npe) {
				if (retries >= CONNECT_RETRIES) {
					throw new DumpException(
							"Error requesting dump using the JMX Connection. Remote VM returned nothing.\n"
									+ "You can try to reconnect or just simply try to request a dump again.");
				}
				try {
					// workaround for unstable connections.
					Thread.sleep(1000);
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
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
	private String getCurrentDateAsString() {
		SimpleDateFormat sdfDate = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
		return sdfDate.format(new Date());
	}

	private String dumpThreadInfo() {
		StringBuilder dump = new StringBuilder();

		dump.append(getCurrentDateAsString());
		dump.append(dumpPrefix);
		dump.append("\n");

		long[] tids = threadMbean.getAllThreadIds();
		ThreadInfo[] threadInfos = threadMbean.getThreadInfo(tids, Integer.MAX_VALUE);

		for (ThreadInfo threadInfo : threadInfos) {
			dump.append(getThreadInfoDump(threadInfo));
		}

		return dump.toString();
	}

	private String dumpThreadInfoWithLocks() {
		StringBuilder dump = new StringBuilder();

		dump.append(getCurrentDateAsString());
		dump.append(dumpPrefix);
		dump.append("\n");

		ThreadInfo[] threadInfos = threadMbean.dumpAllThreads(true, true);

		for (ThreadInfo threadInfo : threadInfos) {
			dump.append(getThreadInfoDump(threadInfo));

			LockInfo[] syncs = threadInfo.getLockedSynchronizers();
			dump.append(getLockInfoDump(syncs));

			MonitorInfo[] monitors = threadInfo.getLockedMonitors();
			dump.append(getMonitorInfoDump(threadInfo, monitors));
		}
		dump.append("\n");

		return dump.toString();
	}

	private String getThreadInfoDump(ThreadInfo threadInfo) {
		StringBuilder dump = new StringBuilder();

		dump.append(getCoreThreadInfoDump(threadInfo));

		// print stack trace with locks
		StackTraceElement[] stacktrace = threadInfo.getStackTrace();
		MonitorInfo[] monitors = threadInfo.getLockedMonitors();

		for (int i = 0; i < stacktrace.length; i++) {
			StackTraceElement ste = stacktrace[i];
			dump.append(INDENT + "at " + ste.toString());
			dump.append("\n");

			for (MonitorInfo mi : monitors) {
				if (mi.getLockedStackDepth() == i) {
					dump.append(INDENT + "  - locked " + mi);
					dump.append("\n");
				}
			}
		}
		dump.append("\n");

		return dump.toString();
	}

	private String getCoreThreadInfoDump(ThreadInfo threadInfo) {
		StringBuilder sb = new StringBuilder();

		sb.append("\"").append(threadInfo.getThreadName()).append("\"").append(" nid=").append(threadInfo.getThreadId())
				.append(" state=").append(threadInfo.getThreadState());

		if (threadInfo.getLockName() != null && threadInfo.getThreadState() != Thread.State.BLOCKED) {
			String[] lockInfo = threadInfo.getLockName().split("@");
			
			sb.append("\n").append(INDENT).append("- waiting on <0x").append(lockInfo[1]).append("> (a ")
					.append(lockInfo[0]).append(")");
			sb.append("\n").append(INDENT).append("- locked <0x").append(lockInfo[1]).append("> (a ")
					.append(lockInfo[0]).append(")");
			
		} else if (threadInfo.getLockName() != null && threadInfo.getThreadState() == Thread.State.BLOCKED) {
			String[] lockInfo = threadInfo.getLockName().split("@");
			sb.append("\n").append(INDENT).append("- waiting to lock <0x").append(lockInfo[1]).append("> (a ")
					.append(lockInfo[0]).append(")");
		}

		if (threadInfo.isSuspended()) {
			sb.append(" (suspended)");
		}
		
		if (threadInfo.isInNative()) {
			sb.append(" (running in native)");
		}

		sb.append("\n");
		if (threadInfo.getLockOwnerName() != null) {
			sb.append(INDENT).append(" owned by ").append(threadInfo.getLockOwnerName()).append(" id=")
					.append(threadInfo.getLockOwnerId());
			sb.append("\n");
		}

		return sb.toString();
	}

	private String getMonitorInfoDump(ThreadInfo threadInfo, MonitorInfo[] monitors) {
		StringBuilder dump = new StringBuilder();

		dump.append(INDENT).append("Locked monitors: count = ").append(monitors.length);

		for (MonitorInfo info : monitors) {
			dump.append(INDENT).append("  - ").append(info).append(" locked at \n");
			dump.append(INDENT).append("      ").append(info.getLockedStackDepth()).append(" ")
					.append(info.getLockedStackFrame());
			dump.append("\n");
		}

		return dump.toString();
	}

	private String getLockInfoDump(LockInfo[] locks) {
		StringBuilder dump = new StringBuilder();

		dump.append(INDENT).append("Locked synchronizers: count = ").append(locks.length);
		dump.append("\n");

		for (LockInfo info : locks) {
			dump.append(INDENT).append("  - ").append(info);
			dump.append("\n");
		}
		dump.append("\n");

		return dump.toString();
	}

	/**
	 * Checks if any threads are deadlocked. If any, get the thread dump
	 * information.
	 */
	public String getDeadlockData() {
		StringBuilder dump = new StringBuilder();

		if (FIND_DEADLOCKED_THREADS.equals(findDeadlocksMethodName) && threadMbean.isSynchronizerUsageSupported()) {
			long[] deadlickedThreads = threadMbean.findDeadlockedThreads();
			if (deadlickedThreads == null) {
				return "";
			}

			dump.append("\n\nFound one Java-level deadlock:\n");
			dump.append("==============================\n");

			ThreadInfo[] infos = threadMbean.getThreadInfo(deadlickedThreads, true, true);
			for (ThreadInfo info : infos) {
				dump.append(getThreadInfoDump(info));
				dump.append(getLockInfoDump(info.getLockedSynchronizers()));
				dump.append("\n");
			}

		} else {
			long[] monitorDeadlockThreads = threadMbean.findMonitorDeadlockedThreads();
			if (monitorDeadlockThreads == null) {
				return "";
			}
			
			dump.append("\n\nFound one Java-level deadlock:\n");
			dump.append("==============================\n");

			ThreadInfo[] infos = threadMbean.getThreadInfo(monitorDeadlockThreads, Integer.MAX_VALUE);
			for (ThreadInfo info : infos) {
				dump.append(getThreadInfoDump(info));
			}

		}

		return (dump.toString());
	}

	private void parseMBeanInfo() throws IOException, DumpException {
		try {
			MBeanOperationInfo[] operations = server.getMBeanInfo(objname).getOperations();
			setDumpPrefix();

			// look for findDeadlockedThreads operations;
			boolean found = false;
			for (MBeanOperationInfo op : operations) {
				if (op.getName().equals(findDeadlocksMethodName)) {
					found = true;
					break;
				}
			}
			if (!found) {
				/*
				 * if findDeadlockedThreads operation doesn't exist, the target
				 * VM is running on JDK 5 and details about synchronizers and
				 * locks cannot be dumped.
				 */
				findDeadlocksMethodName = FIND_MONITOR_DEADLOCKED_THREADS;

				/*
				 * hack for jconsole dumping itself, for strange reasons, vm
				 * doesn't provide findDeadlockedThreads, but 1.5 ops fail with
				 * an error.
				 */
				// System.out.println("java.version=" +javaVersion);
				canDumpLocks = javaVersion.startsWith("1.6");
			}
		} catch (IntrospectionException e) {
			throw new DumpException(e);
		} catch (InstanceNotFoundException e) {
			throw new DumpException(e);
		} catch (ReflectionException e) {
			throw new DumpException(e);
		}
	}

	/**
	 * reset mbean server connection
	 * 
	 * @param mbs
	 * @throws IOException 
	 */
	void setMBeanServerConnection(MBeanServerConnection mbs) throws IOException {
		server = mbs;
		threadMbean = (ThreadMXBean) ManagementFactory.newPlatformMXBeanProxy(server,
				ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);
	}
}