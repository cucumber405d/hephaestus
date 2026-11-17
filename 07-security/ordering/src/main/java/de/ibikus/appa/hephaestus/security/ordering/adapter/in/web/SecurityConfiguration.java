package de.ibikus.appa.hephaestus.security.ordering.adapter.in.web;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Ordering is a stateless OAuth2 resource server (issuer, keys and audience in application.yaml).
 * Placing an order needs the realm role {@code customer}; reading one needs any valid token, the owner check
 * (or {@code admin}) happens in the application service.
 */
// Not in the schema migration Job (no web server, no HttpSecurity)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    static final String ADMIN = "ROLE_admin";

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        var authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(new RealmRolesConverter());
        http.authorizeHttpRequests(requests -> requests
                        // Error rendering after a handled failure must not turn into a second 401/403
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders")
                        .hasRole("customer")
                        .requestMatchers(HttpMethod.GET, "/api/orders/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(authentication)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bearer tokens, no cookies: CSRF protection has nothing to protect here (the Gateway has it)
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
