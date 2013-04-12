package com.pololpoly.util;

@SuppressWarnings("serial")
public class DumpException extends Exception {

	public DumpException(String string) {
		super(string);
	}
	
	public DumpException(Exception e) {
		super(e);
	}

}
