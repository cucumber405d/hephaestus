package de.ibikus.appa.hephaestus.observability.ordering.messaging;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/** Hands Boot's OpenTelemetry SDK to the Logback appender declared in logback-spring.xml (logs to Loki over OTLP). */
@Component
class LogExport implements InitializingBean {

    private final OpenTelemetry openTelemetry;

    LogExport(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
