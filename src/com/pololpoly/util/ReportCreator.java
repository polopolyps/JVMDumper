package com.pololpoly.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeMBeanException;
import javax.management.openmbean.CompositeData;

public class ReportCreator {

	private static final String COMPOSITE_DATA_TYPE = "javax.management.openmbean.CompositeData";
	private static Logger LOGGER = Logger.getLogger(ReportCreator.class.getName());

	public void createHtmlReport(String outputFilePath, MBeanServerConnection server) {

		System.out.println("Creating JMX Mbean dump to file " + outputFilePath);
		if (outputFilePath == null || outputFilePath.length() == 0) {
			LOGGER.log(Level.WARNING, "Output path is empty, report will not be generated.");
		}

		File f = new File(outputFilePath);

		try {
			PrintStream printStream = new PrintStream(f);

			printStream.println("<html>");
			printStream.println("<head><title>JMX Mbean Listing</title></head>");
			printStream.println("<body><table>");

			try {
				for (ObjectName mbeanName : server.queryNames(null, null)) {

					printStream.println("<tr><td colspan='4'>&nbsp;</td></tr>");
					printStream.println("<tr><td>MBean:</td><td colspan='3'>" + mbeanName + "</td></tr>");

					MBeanAttributeInfo[] attributes = getMBeanInfo(server, mbeanName).getAttributes();
					printAttributes(server, printStream, mbeanName, attributes);
				}
			} catch (IOException e) {
				printStream.print("<tr><td colspan=3>" + e.getMessage() + "</td></tr>");
			} catch (MBeanException e) {
				printStream.print("<tr><td colspan=3>" + e.getMessage() + "</td></tr>");
			}

			printStream.println("</table></body></html>");
			printStream.flush();

		} catch (FileNotFoundException e) {
			LOGGER.log(Level.WARNING, "Could not create a report: " + e.getMessage(), e);
		}
	}

	private void printAttributes(MBeanServerConnection server, PrintStream printStream, final ObjectName mbeanName,
			MBeanAttributeInfo[] attributes) throws IOException {

		for (MBeanAttributeInfo attributeInfo : attributes) {

			String name = attributeInfo.getName();
			String type = attributeInfo.getType();

			printStream.print("<tr><td>&nbsp;</td>");
			printStream.print("<td>" + name + "</td><td>" + type + "</td><td>");

			try {
				Object attribute = getMBeanAttribute(server, mbeanName, name);

				if (attribute == null) {
					printStream.print("<font color='#660000'>null</font>");

				} else {

					if (COMPOSITE_DATA_TYPE.equalsIgnoreCase(type)) {
						if (attribute instanceof CompositeData) {
							for (Object o : ((CompositeData) attribute).values()) {
								printStream.print(o.toString());
							}
						}
					}

					printStream.print(attribute.toString());
				}

			} catch (MBeanException e) {
				printStream.print("<font color='#660000'>could not get the " + name + " attribute</font>");
			}

			printStream.println("</td></tr>");
		}
	}

	private Object getMBeanAttribute(MBeanServerConnection server, final ObjectName mbeanName, String name)
			throws MBeanException {

		try {
			return server.getAttribute(mbeanName, name);

		} catch (AttributeNotFoundException e) {
			throw new MBeanException(e);
		} catch (InstanceNotFoundException e) {
			throw new MBeanException(e);
		} catch (ReflectionException e) {
			throw new MBeanException(e);
		} catch (IOException e) {
			throw new MBeanException(e);
		} catch (RuntimeMBeanException e) {
			throw new MBeanException(e);
		}
	}

	private MBeanInfo getMBeanInfo(MBeanServerConnection server, ObjectName mbeanName) throws MBeanException {

		try {
			return server.getMBeanInfo(mbeanName);

		} catch (InstanceNotFoundException e) {
			throw new MBeanException(e);
		} catch (IntrospectionException e) {
			throw new MBeanException(e);
		} catch (ReflectionException e) {
			throw new MBeanException(e);
		} catch (IOException e) {
			throw new MBeanException(e);
		}
	}

}
