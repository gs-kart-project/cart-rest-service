package com.gskart.cart.security.models;

import org.springframework.stereotype.Component;

@Component
public class GSKartResourceServerUserContext {
    private final ThreadLocal<GSKartResourceServerUser> currentUser = new ThreadLocal<>();

    public void setGskartResourceServerUser(GSKartResourceServerUser gskartResourceServerUser) {
        currentUser.set(gskartResourceServerUser);
    }

    public GSKartResourceServerUser getGskartResourceServerUser() {
        return currentUser.get();
    }

    public void clear() {
        currentUser.remove();
    }
}
