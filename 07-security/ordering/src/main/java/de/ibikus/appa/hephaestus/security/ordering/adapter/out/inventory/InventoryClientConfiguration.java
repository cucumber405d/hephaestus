package de.ibikus.appa.hephaestus.security.ordering.adapter.out.inventory;

import java.net.http.HttpClient;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.ApiVersionInserter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Builds the Inventory client explicitly: base URL, connect and read timeouts, the API-Version header for the
 * version declared on each interface method, and a service token (OAuth2 client credentials, registration
 * {@value #REGISTRATION}) on every call. (@ImportHttpServices could register the proxy with less code; the
 * explicit factory keeps every knob visible for learning.)
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InventoryClientProperties.class)
class InventoryClientConfiguration {

    /** spring.security.oauth2.client.registration.inventory: client "ordering", grant client_credentials. */
    static final String REGISTRATION = "inventory";

    /**
     * Ordering calls Inventory as itself, never on behalf of the user: the token is the service's, cached by the
     * authorized client service under one fixed principal and renewed shortly before it expires.
     */
    static final Authentication SERVICE = new AnonymousAuthenticationToken(
            "ordering", "ordering-service", AuthorityUtils.createAuthorityList("ROLE_SERVICE"));

    /** Without a manager (the schema migration Job runs without a web server) the client simply has no token. */
    @Bean
    InventoryHttpApi inventoryHttpApi(
            RestClient.Builder builder,
            InventoryClientProperties properties,
            ObjectProvider<OAuth2AuthorizedClientManager> clients) {
        var manager = clients.getIfAvailable();
        return manager == null ? create(builder, properties) : create(builder, properties, serviceToken(manager));
    }

    /**
     * Works outside an HTTP request too (unlike the default, request-bound manager): the token belongs to the
     * service, not to a browser session.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    OAuth2AuthorizedClientManager serviceClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
        return clientCredentialsManager(registrations, authorizedClients);
    }

    static OAuth2AuthorizedClientManager clientCredentialsManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return manager;
    }

    /** Adds "Authorization: Bearer <service token>" to each request, fetching or renewing the token as needed. */
    static ClientHttpRequestInterceptor serviceToken(OAuth2AuthorizedClientManager clients) {
        var interceptor = new OAuth2ClientHttpRequestInterceptor(clients);
        interceptor.setClientRegistrationIdResolver(request -> REGISTRATION);
        interceptor.setPrincipalResolver(request -> SERVICE);
        return interceptor;
    }

    /** Also used by tests to build a client against a stub server without a Spring context. */
    static InventoryHttpApi create(
            RestClient.Builder builder,
            InventoryClientProperties properties,
            ClientHttpRequestInterceptor... interceptors) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                // HTTP/1.1: no h2c upgrade attempt against a plain-HTTP Tomcat
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        var restClient = builder.baseUrl(properties.url().toString())
                .requestFactory(requestFactory)
                .apiVersionInserter(ApiVersionInserter.useHeader("API-Version"))
                .requestInterceptors(list -> list.addAll(List.of(interceptors)))
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(InventoryHttpApi.class);
    }
}
