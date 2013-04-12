package com.pololpoly.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public class HostData {

	private static final Logger LOGGER = Logger.getLogger(HostData.class.getName());

	private String hostName;
	private int port;
	private String userName;
	private String password;

	public HostData(String[] data) {

		if (data.length > 1) {
			hostName = data[0];
			try {
				port = Integer.parseInt(data[1]);
			} catch (NumberFormatException e) {
				LOGGER.log(Level.WARNING, "Could not parse port number (" + data[1] + ") : " + e.getMessage());
			}
		} else {
			hostName = "";
		}

		if (data.length > 3) {
			userName = data[2];
			password = data[3];
		} else {
			userName = "";
			password = "";
		}
	}

	public String getHostName() {
		return hostName;
	}

	public int getPort() {
		return port;
	}

	public String getUserName() {
		return userName;
	}

	public String getPassword() {
		return password;
	}

}