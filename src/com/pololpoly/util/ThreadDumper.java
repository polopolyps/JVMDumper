package com.pololpoly.util;

import java.io.IOException;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

public class ThreadDumper {

	private static final String NEW_LINE = "\n";

	private static final Logger LOGGER = Logger.getLogger(ThreadDumper.class.getName());

	private static final String FIND_DEADLOCKED_THREADS = "findDeadlockedThreads";
	private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
	private static final String INDENT = "    ";
	private static final int CONNECT_RETRIES = 10;

	private final String dumpPrefix;
	private final boolean canDumpLocks;

	/**
	 * Constructs a ThreadMonitor object to get thread information in a remote
	 * JVM.
	 * 
	 * @throws DumpException
	 */
	public ThreadDumper(MBeanServerConnection connection) throws IOException {
		RuntimeMXBean runtimeMxBean = ManagementFactory.newPlatformMXBeanProxy(connection,
				ManagementFactory.RUNTIME_MXBEAN_NAME, RuntimeMXBean.class);

		String javaVersion = runtimeMxBean.getVmVersion();
		dumpPrefix = "\nFull thread dump " + runtimeMxBean.getVmName() + " " + javaVersion + NEW_LINE;

		/*
		 * hack for jconsole dumping itself, for strange reasons, vm doesn't
		 * provide findDeadlockedThreads, but 1.5 ops fail with an error.
		 */
		canDumpLocks = javaVersion.startsWith("1.6");
	}

	private String getDumpPrefix() {
		return dumpPrefix;
	}

	private boolean canDumpLocks() {
		return canDumpLocks;
	}

	public String getThreadDump(ThreadMXBean threadMxBean) throws DumpException {
		StringBuilder dump = new StringBuilder();
		int retries = 0;

		while (retries < CONNECT_RETRIES) {
			try {
				if (canDumpLocks()) {
					if (threadMxBean.isObjectMonitorUsageSupported() && threadMxBean.isSynchronizerUsageSupported()) {
						/*
						 * Print lock info if both object monitor usage and
						 * synchronizer usage are supported. This sample code
						 * can be modified to handle if either monitor usage or
						 * synchronizer usage is supported.
						 */
						ThreadInfo[] threadsInfo = threadMxBean.dumpAllThreads(true, true);
						dump.append(getThreadsWithLockInfoDump(threadsInfo));
					}
				} else {
					ThreadInfo[] threadsInfo = threadMxBean.getThreadInfo(threadMxBean.getAllThreadIds(),
							Integer.MAX_VALUE);
					dump.append(getThreadsInfoDump(threadsInfo));
				}

				break;

			} catch (NullPointerException e) {
				// where can this come from???
				LOGGER.log(Level.WARNING, "Could not get a thread dump: " + e.getMessage(), e);

				if (retries >= CONNECT_RETRIES) {
					throw new DumpException(
							"Error requesting dump using the JMX Connection. Remote VM returned nothing.\n"
									+ "You can try to reconnect or simply try to request a dump again.");
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

		return dump.toString();
	}

	/**
	 * create dump date similar to format used by 1.6 VMs
	 * 
	 * @return dump date (e.g. 2007-10-25 08:00:00)
	 */
	private String getCurrentDateAsString() {
		DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
		return dateFormat.format(new Date());
	}

	private String getDumpHeader() {
		StringBuilder dump = new StringBuilder();

		dump.append(getCurrentDateAsString());
		dump.append(getDumpPrefix());
		dump.append(NEW_LINE);

		return dump.toString();
	}

	private String getThreadsInfoDump(ThreadInfo threadInfos[]) {
		StringBuilder dump = new StringBuilder();

		dump.append(getDumpHeader());

		for (ThreadInfo info : threadInfos) {
			dump.append(getSingleThreadInfoDump(info));
		}

		return dump.toString();
	}

	private String getThreadsWithLockInfoDump(ThreadInfo threadInfos[]) {
		StringBuilder dump = new StringBuilder();
		dump.append(getDumpHeader());

		for (ThreadInfo info : threadInfos) {
			dump.append(getSingleThreadInfoDump(info));
			dump.append(getLockInfoDump(info.getLockedSynchronizers()));
			dump.append(getMonitorInfoDump(info, info.getLockedMonitors()));
		}
		dump.append(NEW_LINE);

		return dump.toString();
	}

	private String getSingleThreadInfoDump(ThreadInfo threadInfo) {
		StringBuilder dump = new StringBuilder();
		dump.append(getCoreThreadInfoDump(threadInfo));

		// print stack trace with locks
		StackTraceElement[] stacktrace = threadInfo.getStackTrace();
		
		for (int i = 0; i < stacktrace.length; i++) {
			StackTraceElement ste = stacktrace[i];
			dump.append(INDENT).append("at ").append(ste.toString());
			dump.append(NEW_LINE);

			for (MonitorInfo monitorInfo : threadInfo.getLockedMonitors()) {
				if (monitorInfo.getLockedStackDepth() == i) {
					dump.append(INDENT).append("  - locked ").append(monitorInfo);
					dump.append(NEW_LINE);
				}
			}
		}
		dump.append(NEW_LINE);

		return dump.toString();
	}

	private String getCoreThreadInfoDump(ThreadInfo threadInfo) {
		StringBuilder sb = new StringBuilder();

		sb.append("\"").append(threadInfo.getThreadName()).append("\"").append(" nid=")
				.append(threadInfo.getThreadId()).append(" state=").append(threadInfo.getThreadState());

		if (threadInfo.getLockName() != null && threadInfo.getThreadState() != Thread.State.BLOCKED) {
			String[] lockInfo = threadInfo.getLockName().split("@");

			sb.append(NEW_LINE).append(INDENT).append("- waiting on <0x").append(lockInfo[1]).append("> (a ")
					.append(lockInfo[0]).append(")");
			sb.append(NEW_LINE).append(INDENT).append("- locked <0x").append(lockInfo[1]).append("> (a ")
					.append(lockInfo[0]).append(")");

		} else if (threadInfo.getLockName() != null && threadInfo.getThreadState() == Thread.State.BLOCKED) {
			String[] lockInfo = threadInfo.getLockName().split("@");
			sb.append(NEW_LINE).append(INDENT).append("- waiting to lock <0x").append(lockInfo[1]).append("> (a ")
					.append(lockInfo[0]).append(")");
		}

		if (threadInfo.isSuspended()) {
			sb.append(" (suspended)");
		}

		if (threadInfo.isInNative()) {
			sb.append(" (running in native)");
		}

		sb.append(NEW_LINE);
		if (threadInfo.getLockOwnerName() != null) {
			sb.append(INDENT).append(" owned by ").append(threadInfo.getLockOwnerName()).append(" id=")
					.append(threadInfo.getLockOwnerId());
			sb.append(NEW_LINE);
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
			dump.append(NEW_LINE);
		}

		return dump.toString();
	}

	private String getLockInfoDump(LockInfo[] locks) {
		StringBuilder dump = new StringBuilder();

		dump.append(INDENT).append("Locked synchronizers: count = ").append(locks.length);
		dump.append(NEW_LINE);

		for (LockInfo info : locks) {
			dump.append(INDENT).append("  - ").append(info);
			dump.append(NEW_LINE);
		}
		dump.append(NEW_LINE);

		return dump.toString();
	}

	/**
	 * Checks if any threads are deadlocked. If any, get the thread dump
	 * information.
	 * 
	 * @throws DumpException
	 * @throws IOException
	 */
	public String getDeadlockData(MBeanServerConnection connection, ThreadMXBean threadMxBean) throws IOException, DumpException {
		StringBuilder dump = new StringBuilder();

		if (isFindDeadlocksMethodSupported(connection) && threadMxBean.isSynchronizerUsageSupported()) {
			long[] deadlockedThreads = threadMxBean.findDeadlockedThreads();
			if (deadlockedThreads == null) {
				return "";
			}

			dump.append("\n\nFound one Java-level deadlock:\n");
			dump.append("==============================\n");

			ThreadInfo[] infos = threadMxBean.getThreadInfo(deadlockedThreads, true, true);
			dump.append(getDeadlockedThreadsInfoDump(infos));

		} else {
			long[] monitorDeadlockThreads = threadMxBean.findMonitorDeadlockedThreads();
			if (monitorDeadlockThreads == null) {
				return "";
			}

			dump.append("\n\nFound one Java-level deadlock:\n");
			dump.append("==============================\n");

			ThreadInfo[] infos = threadMxBean.getThreadInfo(monitorDeadlockThreads, Integer.MAX_VALUE);
			dump.append(getDeadlockedThreadsInfoDump(infos));

		}

		return dump.toString();
	}
	
	private String getDeadlockedThreadsInfoDump(ThreadInfo[] infos) {
		StringBuilder dump = new StringBuilder();
		
		for (ThreadInfo info : infos) {
			dump.append(getSingleThreadInfoDump(info));
			dump.append(getLockInfoDump(info.getLockedSynchronizers()));
			dump.append(NEW_LINE);
		}
		
		return dump.toString();
	}

	private boolean isFindDeadlocksMethodSupported(MBeanServerConnection connection) throws IOException, DumpException {
		try {
			ObjectName objectName = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
			MBeanOperationInfo[] operations = connection.getMBeanInfo(objectName).getOperations();

			// look for findDeadlockedThreads operations;
			for (MBeanOperationInfo op : operations) {
				if (FIND_DEADLOCKED_THREADS.equals(op.getName())) {
					return true;
				}
			}

			/*
			 * if findDeadlockedThreads operation doesn't exist, the target VM
			 * is running on JDK 5 and details about synchronizers and locks
			 * cannot be dumped.
			 */
			return false;

		} catch (IntrospectionException e) {
			throw new DumpException(e);
		} catch (InstanceNotFoundException e) {
			throw new DumpException(e);
		} catch (ReflectionException e) {
			throw new DumpException(e);
		} catch (MalformedObjectNameException e) {
			throw new DumpException(e);
		}
	}

}