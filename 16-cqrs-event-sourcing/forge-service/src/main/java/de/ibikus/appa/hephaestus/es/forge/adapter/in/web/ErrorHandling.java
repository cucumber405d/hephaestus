package de.ibikus.appa.hephaestus.es.forge.adapter.in.web;

import de.ibikus.appa.hephaestus.es.forge.application.projection.NotRebuildable;
import de.ibikus.appa.hephaestus.es.forge.application.projection.ProjectionNotFound;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.ConcurrencyConflict;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderRefused;
import de.ibikus.appa.hephaestus.es.forge.application.WorkOrderNotFound;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Application exceptions to HTTP: the application layer knows no HTTP status. */
@RestControllerAdvice
class ErrorHandling {

    /** The command does not fit the work order's state (already taken, not yet allocated, ...). */
    @ExceptionHandler(WorkOrderRefused.class)
    ProblemDetail refused(WorkOrderRefused e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Another writer appended to the stream first; the client may reload and decide again. */
    @ExceptionHandler(ConcurrencyConflict.class)
    ProblemDetail concurrency(ConcurrencyConflict e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ProjectionNotFound.class)
    ProblemDetail noProjection(ProjectionNotFound e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(NotRebuildable.class)
    ProblemDetail notRebuildable(NotRebuildable e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(WorkOrderNotFound.class)
    ProblemDetail notFound(WorkOrderNotFound e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
