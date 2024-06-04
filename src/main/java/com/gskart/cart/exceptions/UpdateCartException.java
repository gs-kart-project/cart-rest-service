package com.gskart.cart.exceptions;

public class UpdateCartException extends Exception{
    public UpdateCartException(String message, Throwable cause){
        super(message, cause);
    }

    public UpdateCartException(String message){
        super(message);
    }
}
