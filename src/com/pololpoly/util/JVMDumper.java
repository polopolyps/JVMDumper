package com.pololpoly.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class JVMDumper {

	private static final String DATE_FORMAT = "yyyy-MM-dd-HH-mm-ss";
	private static final Logger LOGGER = Logger.getLogger(JVMDumper.class.getName());

	private MBeanServerConnection server;

	private boolean isEmpty(String string) {
		return string == null || string.length() == 0;
	}

	public void init(String hostname, int port, String userName, String password) throws IOException {

		Map<String, String[]> properties = new Hashtable<String, String[]>();

		if (!isEmpty(userName) && !isEmpty(password)) {
			System.out.println("User name is " + userName);
			System.out.println("Password is " + password);
			properties.put("jmx.remote.credentials", new String[] { userName, password });
		} else {
			System.out.println("No credentials provided.");
		}

		connect(hostname, port, properties);
	}

	private void connect(String hostname, int port, Map<String, String[]> properties) throws IOException {
		String hostKey = "/jndi/rmi://" + hostname + ":" + port + "/jmxrmi";
		System.out.println("Connecting to " + hostKey);

		JMXServiceURL url = new JMXServiceURL("rmi", "", 0, hostKey);
		JMXConnector jmxc = JMXConnectorFactory.connect(url, properties);
		server = jmxc.getMBeanServerConnection();
	}

	public void dumpToFile(String filePath) throws IOException, DumpException {
		System.out.println("Creating thread dump to file " + filePath);

		if (filePath != null) {

			PrintStream p = new PrintStream(new File(filePath));
			try {
				ThreadDumper monitor = new ThreadDumper(server);

				p.println(monitor.threadDump());

				String deadlocks = monitor.getDeadlockData();
				if (deadlocks != null) {
					p.println(deadlocks);
				}

			} finally {
				p.close();
			}
		}
	}

	private static Map<String, String> getHostsFromFile(String fileName) throws IOException {
		Map<String, String> hosts = new HashMap<String, String>();
		BufferedReader reader = new BufferedReader(new FileReader(fileName));

		try {
			String line = null;
			while ((line = reader.readLine()) != null) {
				String hostDetails[] = line.split(",");
				if (hostDetails.length > 1) {
					hosts.put(hostDetails[0], hostDetails[1]);
				}
			}
		} finally {
			reader.close();
		}

		return hosts;
	}

	private MBeanServerConnection getServer() {
		return server;
	}

	private String getCurrentDateAsString() {
		DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
		return dateFormat.format(new Date());
	}

	private void processHosts(Map<String, String> hosts, String outputPath) {
		for (String hostKey : hosts.keySet()) {

			String data[] = hosts.get(hostKey).split(":");
			HostData hostData = new HostData(data);

			String outputPrefix = outputPath + hostKey + "-" + hostData.getHostName() + "-" + getCurrentDateAsString();

			try {

				init(hostData.getHostName(), hostData.getPort(), hostData.getUserName(), hostData.getPassword());
				dumpToFile(outputPrefix + ".tdump");

				new ReportCreator().createHtmlReport(outputPrefix + "-mbean.html", getServer());

			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "IO error occured : " + e.getMessage());
			} catch (DumpException e) {
				LOGGER.log(Level.WARNING, "Error while taking a dump : " + e.getMessage());
			}
		}
	}

	private static String getOutputDirPath(String[] args) {
		if (args.length == 2) {
			return args[1].endsWith("/") ? args[1] : args[1] + "/";
		} else {
			return "";
		}
	}

	public static void main(String[] args) {

		if (args.length == 0 || args.length > 2) {
			System.out.println("Usage: java -jar JVMDumper <host-file> <output-dir-path>");
			System.exit(1);
		}

		String hostsFile = args[0];

		try {
			Map<String, String> hosts = getHostsFromFile(hostsFile);
			new JVMDumper().processHosts(hosts, getOutputDirPath(args));

			System.out.println("Done.");

		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Could not read the hosts file (" + hostsFile + "): " + e.getMessage(), e);
		}

	}
}
