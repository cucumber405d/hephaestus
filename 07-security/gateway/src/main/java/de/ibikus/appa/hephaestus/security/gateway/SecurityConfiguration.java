package de.ibikus.appa.hephaestus.security.gateway;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * The Gateway is a Backend for Frontend (BFF): the browser only ever holds an HttpOnly session cookie, the tokens stay
 * in the server-side session.
 * <ul>
 *   <li>login: OAuth2 Authorization Code with PKCE (S256) against Keycloak, as the confidential client
 *       {@code shop-gateway};</li>
 *   <li>{@code /api/**}: without a session 401 (an API caller cannot follow a login redirect), with one the route
 *       relays the user's access token as a Bearer header (TokenRelay in application.yaml);</li>
 *   <li>CSRF: the session cookie is sent by the browser automatically, so every state-changing request must also
 *       carry the token from the readable {@code XSRF-TOKEN} cookie in the {@code X-XSRF-TOKEN} header.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain bff(HttpSecurity http, ClientRegistrationRepository registrations) throws Exception {
        var authorizationRequests = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        // PKCE also for a confidential client: a stolen authorization code is useless without the verifier
        authorizationRequests.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        http.authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2Login(login -> login.authorizationEndpoint(
                        endpoint -> endpoint.authorizationRequestResolver(authorizationRequests)))
                // Registered before oauth2Login's own entry point (which only takes requests accepting HTML), so the
                // API gets 401 and so does any other non-page request; a browser navigation is sent to the login
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                // Cookie XSRF-TOKEN (readable by JavaScript), header X-XSRF-TOKEN, raw value accepted
                .csrf(csrf -> csrf.spa())
                // Local logout (POST /logout with the CSRF header): ends the gateway session. The Keycloak SSO
                // session stays, the next login goes through without a password form.
                .logout(logout -> logout.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()));
        return http.build();
    }

    /** The tokens of a logged-in user live in their HTTP session, nowhere in the browser. */
    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    /**
     * Used by the TokenRelay filter: hands out the session's access token and renews it with the refresh token
     * when it has expired (Keycloak access tokens live five minutes, the session longer).
     */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientRepository authorizedClients) {
        var manager = new DefaultOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build());
        return manager;
    }

    /**
     * The provider is configured with explicit endpoints (no issuer discovery, the public Keycloak address is not
     * reachable from inside the cluster), so Spring does not know the issuer by itself: check "iss" of the ID token
     * against the public issuer explicitly, in addition to the standard OIDC checks (audience, nonce, expiry).
     */
    @Bean
    JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(@Value("${shop.oauth2.issuer}") String issuer) {
        var factory = new OidcIdTokenDecoderFactory();
        factory.setJwtValidatorFactory(registration ->
                new DelegatingOAuth2TokenValidator<>(new OidcIdTokenValidator(registration), new JwtIssuerValidator(issuer)));
        return factory;
    }
}
