package com.gskart.cart.exceptions;

public class DeleteCartException extends Exception {
    public DeleteCartException(String message) {
        super(message);
    }

    public DeleteCartException(String message, Throwable cause){
        super(message, cause);
    }
}
