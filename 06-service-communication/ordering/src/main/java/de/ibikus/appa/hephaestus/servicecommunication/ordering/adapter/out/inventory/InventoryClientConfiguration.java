package de.ibikus.appa.hephaestus.servicecommunication.ordering.adapter.out.inventory;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ApiVersionInserter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Builds the Inventory client explicitly: base URL, connect and read timeouts, and the API-Version header for the
 * version declared on each interface method. (@ImportHttpServices could register the proxy with less code; the
 * explicit factory keeps every knob visible for learning.)
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InventoryClientProperties.class)
class InventoryClientConfiguration {

    @Bean
    InventoryHttpApi inventoryHttpApi(RestClient.Builder builder, InventoryClientProperties properties) {
        return create(builder, properties);
    }

    /** Also used by tests to build a client against a stub server without a Spring context. */
    static InventoryHttpApi create(RestClient.Builder builder, InventoryClientProperties properties) {
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
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(InventoryHttpApi.class);
    }
}
