package de.ibikus.appa.hephaestus.monolith.modular.forge.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class WorkOrderNotFound extends RuntimeException {

    WorkOrderNotFound(String id) {
        super("work order " + id);
    }
}
