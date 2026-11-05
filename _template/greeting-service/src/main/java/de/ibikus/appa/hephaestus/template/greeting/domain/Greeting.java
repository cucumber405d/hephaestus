package de.ibikus.appa.hephaestus.template.greeting.domain;

/** Приветствие адресату. Чистый домен: без зависимостей от Spring и адаптеров. */
public record Greeting(String recipient) {

    public static final int MAX_RECIPIENT_LENGTH = 50;

    public Greeting {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Recipient must not be blank");
        }
        if (recipient.length() > MAX_RECIPIENT_LENGTH) {
            throw new IllegalArgumentException("Recipient must be at most " + MAX_RECIPIENT_LENGTH + " characters");
        }
    }

    public String message() {
        return "Hello, " + recipient + "!";
    }
}
