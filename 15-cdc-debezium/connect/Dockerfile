# Kafka Connect for Strimzi (KafkaConnect.spec.image) with the Debezium PostgreSQL connector plugin.
# Base: the Strimzi 1.2.0 image of the platform's Kafka; plugin: Maven Central.
#   docker build -t hephaestus/cdc-debezium-connect:jib 15-cdc-debezium/connect
ARG STRIMZI_KAFKA_IMAGE=quay.io/strimzi/kafka:1.2.0-kafka-4.3.1
FROM ${STRIMZI_KAFKA_IMAGE}

# Latest stable on 2026-11-25 (3.7 series)
ARG DEBEZIUM_VERSION=3.7.0.Final

USER root
RUN mkdir -p /opt/kafka/plugins/debezium-postgres \
    && curl -fsSL "https://repo1.maven.org/maven2/io/debezium/debezium-connector-postgres/${DEBEZIUM_VERSION}/debezium-connector-postgres-${DEBEZIUM_VERSION}-plugin.tar.gz" \
       | tar -xz -C /opt/kafka/plugins/debezium-postgres
USER 1001
