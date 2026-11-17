package de.ibikus.appa.hephaestus.security.inventory.adapter.in.web;

import java.net.URI;
import java.time.ZonedDateTime;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.StandardApiVersionDeprecationHandler;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Version 1 of the stock API is deprecated: responses to it carry the Deprecation (RFC 9745) and Sunset
 * (RFC 8594) headers plus a Link to the migration notes, so clients learn about it from every call.
 * Header name, supported versions and the default come from spring.mvc.apiversion.* in application.yaml.
 */
@Configuration(proxyBeanMethods = false)
class ApiVersioningConfiguration implements WebMvcConfigurer {

    static final ZonedDateTime V1_DEPRECATED = ZonedDateTime.parse("2026-11-17T00:00:00Z");
    static final ZonedDateTime V1_SUNSET = ZonedDateTime.parse("2027-06-30T00:00:00Z");
    static final URI MIGRATION_NOTES = URI.create(
            "https://github.com/cucumber405d/hephaestus/blob/main/07-security/docs/learning-notes.md");

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        var deprecation = new StandardApiVersionDeprecationHandler();
        deprecation
                .configureVersion("1")
                .setDeprecationDate(V1_DEPRECATED)
                .setSunsetDate(V1_SUNSET)
                .setDeprecationLink(MIGRATION_NOTES);
        configurer.setDeprecationHandler(deprecation);
    }
}
