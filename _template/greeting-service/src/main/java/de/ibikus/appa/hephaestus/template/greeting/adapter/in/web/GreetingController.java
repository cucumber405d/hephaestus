package de.ibikus.appa.hephaestus.template.greeting.adapter.in.web;

import de.ibikus.appa.hephaestus.template.greeting.application.GreetingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings")
class GreetingController {

    private final GreetingService greetingService;

    GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/{recipient}")
    GreetingResponse greet(@PathVariable String recipient) {
        return GreetingResponse.from(greetingService.greet(recipient));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRecipient(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid recipient");
        return problem;
    }
}
