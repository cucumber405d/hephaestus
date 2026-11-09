package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

/** A state-changing request arrived without If-Match (RFC 6585: 428 Precondition Required). */
class PreconditionRequiredException extends RuntimeException {

    PreconditionRequiredException(String message) {
        super(message);
    }
}
