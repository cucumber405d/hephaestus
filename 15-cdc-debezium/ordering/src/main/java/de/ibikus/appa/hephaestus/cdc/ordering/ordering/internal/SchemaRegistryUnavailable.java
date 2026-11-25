package de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal;

/** The request could not be serialized: the registry did not answer. The order is not accepted (HTTP 503). */
class SchemaRegistryUnavailable extends RuntimeException {

    SchemaRegistryUnavailable(Throwable cause) {
        super("schema registry unavailable", cause);
    }
}
