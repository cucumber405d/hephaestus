package de.ibikus.appa.hephaestus.es.forge.adapter.in.web;

import de.ibikus.appa.hephaestus.es.forge.application.projection.NotRebuildable;
import de.ibikus.appa.hephaestus.es.forge.application.projection.ProjectionNotFound;
import de.ibikus.appa.hephaestus.es.forge.application.WorkOrderConflict;
import de.ibikus.appa.hephaestus.es.forge.application.WorkOrderNotFound;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Application exceptions to HTTP: the application layer knows no HTTP status. */
@RestControllerAdvice
class ErrorHandling {

    @ExceptionHandler(WorkOrderConflict.class)
    ProblemDetail conflict(WorkOrderConflict e) {
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
