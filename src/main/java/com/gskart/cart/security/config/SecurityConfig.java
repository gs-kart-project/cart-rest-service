package com.gskart.cart.security.config;

import com.gskart.cart.security.models.GSKartResourceServerUser;
import com.gskart.cart.security.models.GSKartResourceServerUserContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests((authorizeRequests) -> {
                    authorizeRequests.anyRequest().permitAll();
                });

        return http.build();
    }

    @Bean
    public GSKartResourceServerUserContext gsKartResourceServerUserContext() {
        GSKartResourceServerUserContext userContext = new GSKartResourceServerUserContext();
        userContext.setGskartResourceServerUser(new GSKartResourceServerUser());
        return userContext;
    }
}
