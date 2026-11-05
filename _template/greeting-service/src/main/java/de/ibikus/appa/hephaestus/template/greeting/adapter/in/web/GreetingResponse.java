package de.ibikus.appa.hephaestus.template.greeting.adapter.in.web;

import de.ibikus.appa.hephaestus.template.greeting.domain.Greeting;

record GreetingResponse(String message) {

    static GreetingResponse from(Greeting greeting) {
        return new GreetingResponse(greeting.message());
    }
}
