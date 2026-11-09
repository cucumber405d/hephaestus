package de.ibikus.appa.hephaestus.restservice.catalog.application.port.out;

/** The response stored for an Idempotency-Key; {@code status == 0} means the original request is still running. */
public record StoredResponse(String requestHash, int status, String body, String location, String etag) {

    public boolean completed() {
        return status > 0;
    }
}
