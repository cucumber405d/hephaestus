package de.ibikus.appa.hephaestus.template.greeting.adapter.in.web;

import de.ibikus.appa.hephaestus.template.greeting.domain.InvalidRecipientException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Перевод доменных исключений в RFC 9457 Problem Details. Только известные исключения — остальное отдаёт Spring (500). */
@RestControllerAdvice(basePackageClasses = GreetingExceptionHandler.class)
class GreetingExceptionHandler {

    @ExceptionHandler(InvalidRecipientException.class)
    ProblemDetail invalidRecipient(InvalidRecipientException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid recipient");
        return problem;
    }
}
