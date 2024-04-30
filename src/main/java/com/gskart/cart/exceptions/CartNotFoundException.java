package com.gskart.cart.exceptions;

public class CartNotFoundException extends Exception{
    public CartNotFoundException(String message, Throwable cause){
        super(message, cause);
    }

    public CartNotFoundException(String message){
        super(message);
    }
}
