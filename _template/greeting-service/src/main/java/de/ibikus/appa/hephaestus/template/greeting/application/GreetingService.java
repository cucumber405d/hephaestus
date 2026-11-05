package de.ibikus.appa.hephaestus.template.greeting.application;

import de.ibikus.appa.hephaestus.template.greeting.domain.Greeting;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    public Greeting greet(String recipient) {
        return new Greeting(recipient);
    }
}
