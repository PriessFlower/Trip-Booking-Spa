package com.bingo.hotel.spa.intl.core.api.common.exception;

public class SysRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 1708344077123335756L;

    public SysRuntimeException() {
        super();
    }

    public SysRuntimeException(String arg0, Throwable arg1, boolean arg2, boolean arg3) {
        super(arg0, arg1, arg2, arg3);
    }

    public SysRuntimeException(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public SysRuntimeException(String arg0) {
        super(arg0);
    }

    public SysRuntimeException(Throwable arg0) {
        super(arg0);
    }

    
}
