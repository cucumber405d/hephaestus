package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryInUseException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryNotFoundException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.DuplicateCategoryNameException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.InvalidValueException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemNotFoundException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.UnknownCategoryException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.VersionConflictException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the service as RFC 9457 Problem Details with a stable {@code type} URI. Domain exceptions are
 * mapped explicitly; Spring MVC's own exceptions (validation, malformed JSON, 415, ...) get a type by status.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    static final String TYPE_BASE = "https://hephaestus.ibikus.de/problems/";

    @ExceptionHandler({CategoryNotFoundException.class, ItemNotFoundException.class})
    ProblemDetail notFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", ex.getMessage());
    }

    @ExceptionHandler(DuplicateCategoryNameException.class)
    ProblemDetail duplicateCategory(DuplicateCategoryNameException ex) {
        return problem(HttpStatus.CONFLICT, "duplicate-category", "Duplicate category", ex.getMessage());
    }

    @ExceptionHandler(CategoryInUseException.class)
    ProblemDetail categoryInUse(CategoryInUseException ex) {
        return problem(HttpStatus.CONFLICT, "category-in-use", "Category in use", ex.getMessage());
    }

    @ExceptionHandler(UnknownCategoryException.class)
    ProblemDetail unknownCategory(UnknownCategoryException ex) {
        return problem(HttpStatusCode.valueOf(422), "unknown-category", "Unknown category", ex.getMessage());
    }

    @ExceptionHandler(VersionConflictException.class)
    ProblemDetail versionConflict(VersionConflictException ex) {
        return problem(HttpStatus.PRECONDITION_FAILED, "precondition-failed", "Precondition failed", ex.getMessage());
    }

    @ExceptionHandler(InvalidValueException.class)
    ProblemDetail invalidValue(InvalidValueException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage());
    }

    /**
     * The generated API interfaces carry {@code @Validated}, so Spring's method-validation proxy checks parameters
     * (page size, ...) before MVC does and throws the plain Bean Validation exception.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException ex) {
        var body = problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "Request validation failed");
        body.setProperty(
                "errors",
                ex.getConstraintViolations().stream()
                        .map(violation ->
                                fieldError(lastNode(violation.getPropertyPath().toString()), violation.getMessage()))
                        .toList());
        return body;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var body = problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "Request validation failed");
        body.setProperty(
                "errors",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> fieldError(error.getField(), error.getDefaultMessage()))
                        .toList());
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var body = problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "Request validation failed");
        body.setProperty(
                "errors",
                ex.getParameterValidationResults().stream()
                        .flatMap(result -> result.getResolvableErrors().stream()
                                .map(error -> fieldError(
                                        result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                        .toList());
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var body = problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "Malformed request body");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Gives Spring's own problems (415, 405, type mismatch, ...) a type URI instead of about:blank. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem && "about:blank".equals(String.valueOf(problem.getType()))) {
            problem.setType(URI.create(TYPE_BASE + slugFor(status)));
        }
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }

    static ProblemDetail problem(HttpStatusCode status, String slug, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + slug));
        problem.setTitle(title);
        return problem;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", Objects.toString(field, "request"), "message", Objects.toString(message, "invalid"));
    }

    /** "searchItems.size" -> "size". */
    private static String lastNode(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
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
