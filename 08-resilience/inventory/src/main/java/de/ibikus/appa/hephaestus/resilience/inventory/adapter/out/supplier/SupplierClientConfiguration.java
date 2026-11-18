package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * The plain supplier client: the timeouts here are the innermost protection (a call never hangs longer than the
 * read timeout, whatever mechanism wraps it).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SupplierProperties.class, SupplyProperties.class})
class SupplierClientConfiguration {

    @Bean
    SupplierHttpApi supplierHttpApi(RestClient.Builder builder, SupplierProperties properties) {
        return create(builder, properties);
    }

    /** Also used by tests against a stub server, without a Spring context. */
    static SupplierHttpApi create(RestClient.Builder builder, SupplierProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        var restClient = builder.baseUrl(properties.url().toString())
                .requestFactory(requestFactory)
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(SupplierHttpApi.class);
    }
}
