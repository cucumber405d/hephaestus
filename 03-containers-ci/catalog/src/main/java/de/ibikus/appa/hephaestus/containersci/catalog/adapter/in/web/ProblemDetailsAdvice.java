package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.containersci.catalog.domain.InvalidValueException;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.ItemNotFoundException;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.UnknownCategoryException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every error leaves the service as RFC 9457 Problem Details with a stable {@code type} URI (slimmed copy of 02). */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    static final String TYPE_BASE = "https://hephaestus.ibikus.de/problems/";

    private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    @ExceptionHandler(InvalidValueException.class)
    ProblemDetail invalidValue(InvalidValueException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(ItemNotFoundException.class)
    ProblemDetail notFound(ItemNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", ex.getMessage());
    }

    @ExceptionHandler(UnknownCategoryException.class)
    ProblemDetail unknownCategory(UnknownCategoryException ex) {
        return problem(HttpStatusCode.valueOf(422), "unknown-category", "Unknown category", ex.getMessage());
    }

    /** Last resort: the message stays in the log; exception texts carry SQL and host names. */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        LOG.error("Unexpected error", ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal error",
                "The request could not be processed; the error has been logged");
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var body = problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "Malformed request body");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Gives Spring's own problems (type mismatch, 405, 415, ...) a type URI instead of about:blank. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var response = super.handleExceptionInternal(ex, body, headers, status, request);
        if (response != null
                && response.getBody() instanceof ProblemDetail problem
                && (problem.getType() == null
                        || "about:blank".equals(problem.getType().toString()))) {
            problem.setType(URI.create(TYPE_BASE + slugFor(status)));
        }
        return response;
    }

    static ProblemDetail problem(HttpStatusCode status, String slug, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + slug));
        problem.setTitle(title);
        return problem;
    }

    private static String slugFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "invalid-request";
            case 404 -> "not-found";
            case 405 -> "method-not-allowed";
            case 406 -> "not-acceptable";
            case 415 -> "unsupported-media-type";
            default -> "error";
        };
    }
}
