package com.gskart.cart.security.filters;

import com.gskart.cart.security.models.GSKartResourceServerUser;
import com.gskart.cart.security.models.GSKartResourceServerUserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtUserContextFilterTest {

    private final GSKartResourceServerUserContext userContext = new GSKartResourceServerUserContext();
    private final JwtUserContextFilter filter = new JwtUserContextFilter(userContext);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Jwt jwt(String username) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", username)
                .claim("email", username + "@gskart.local")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    @Test
    void populatesUserContextFromAuthenticatedJwtDuringTheChainAndClearsItAfter() throws Exception {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt("cart-user"),
                List.of(new SimpleGrantedAuthority("Customer")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        AtomicReference<GSKartResourceServerUser> seenDuringChain = new AtomicReference<>();
        filter.doFilterInternal(mock(HttpServletRequest.class), mock(HttpServletResponse.class),
                (req, res) -> seenDuringChain.set(userContext.getGskartResourceServerUser()));

        GSKartResourceServerUser seen = seenDuringChain.get();
        assertThat(seen).isNotNull();
        assertThat(seen.getUsername()).isEqualTo("cart-user");
        assertThat(seen.getAuthorities()).extracting(Object::toString).containsExactly("Customer");
        assertThat(userContext.getGskartResourceServerUser()).isNull();
    }

    @Test
    void leavesUserContextUnsetWhenThereIsNoJwtAuthentication() throws Exception {
        AtomicReference<GSKartResourceServerUser> seenDuringChain = new AtomicReference<>();
        filter.doFilterInternal(mock(HttpServletRequest.class), mock(HttpServletResponse.class),
                (req, res) -> seenDuringChain.set(userContext.getGskartResourceServerUser()));

        assertThat(seenDuringChain.get()).isNull();
        assertThat(userContext.getGskartResourceServerUser()).isNull();
    }
}
