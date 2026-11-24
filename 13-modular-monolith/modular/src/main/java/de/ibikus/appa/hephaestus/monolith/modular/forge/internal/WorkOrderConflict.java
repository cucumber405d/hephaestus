package de.ibikus.appa.hephaestus.monolith.modular.forge.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A transition from the wrong state: the smith did it already, or the material is not there yet. */
@ResponseStatus(HttpStatus.CONFLICT)
class WorkOrderConflict extends RuntimeException {

    WorkOrderConflict(String message) {
        super(message);
    }
}
