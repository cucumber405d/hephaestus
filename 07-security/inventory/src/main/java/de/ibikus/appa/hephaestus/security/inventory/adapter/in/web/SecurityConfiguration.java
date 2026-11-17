package de.ibikus.appa.hephaestus.security.inventory.adapter.in.web;

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
 * Inventory is a stateless OAuth2 resource server. Issuer, key set and audience come from
 * spring.security.oauth2.resourceserver.jwt.* in application.yaml.
 * <ul>
 *   <li>stock may be read by any authenticated caller (a user via the Gateway or a service);</li>
 *   <li>reservations need the realm role {@code inventory-reserver}, which only the Ordering service account has:
 *       a user token relayed by the Gateway cannot reserve stock directly.</li>
 * </ul>
 */
// Not in the schema migration Job (no web server, no HttpSecurity)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

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
                        .requestMatchers(HttpMethod.POST, "/api/reservations")
                        .hasRole("inventory-reserver")
                        .requestMatchers(HttpMethod.GET, "/api/stock/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(authentication)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bearer tokens, no cookies: CSRF protection has nothing to protect here
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
