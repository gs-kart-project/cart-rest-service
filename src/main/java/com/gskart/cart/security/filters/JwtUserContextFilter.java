package com.gskart.cart.security.filters;

import com.gskart.cart.security.models.GSKartResourceServerUser;
import com.gskart.cart.security.models.GSKartResourceServerUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Populates the createdBy/modifiedBy ThreadLocal from the JWT the resource-server filter chain
 * already authenticated, so CartService/CartController keep reading it unchanged.
 */
@Component
public class JwtUserContextFilter extends OncePerRequestFilter {

    private final GSKartResourceServerUserContext resourceServerUserContext;

    public JwtUserContextFilter(GSKartResourceServerUserContext resourceServerUserContext) {
        this.resourceServerUserContext = resourceServerUserContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
                resourceServerUserContext.setGskartResourceServerUser(
                        new GSKartResourceServerUser(jwtAuthenticationToken.getToken(), jwtAuthenticationToken.getAuthorities()));
            }
            filterChain.doFilter(request, response);
        } finally {
            resourceServerUserContext.clear();
        }
    }
}
