package com.trip.booking.spa.core.api.common.exception;

public class ParseException extends RuntimeException {

	private static final long serialVersionUID = 1708344077123335756L;

	public ParseException() {
		super();
	}

	public ParseException(String arg0, Throwable arg1, boolean arg2, boolean arg3) {
		super(arg0, arg1, arg2, arg3);
	}

	public ParseException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

	public ParseException(String arg0) {
		super(arg0);
	}

	public ParseException(Throwable arg0) {
		super(arg0);
	}

	
}
