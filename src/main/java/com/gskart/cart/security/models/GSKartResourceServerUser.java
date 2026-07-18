package com.gskart.cart.security.models;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;

@Getter
public class GSKartResourceServerUser implements UserDetails {
    private final String username;
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;

    public GSKartResourceServerUser(Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        this.username = jwt.getSubject();
        this.email = jwt.getClaimAsString("email");
        this.authorities = authorities == null ? Collections.emptyList() : authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
