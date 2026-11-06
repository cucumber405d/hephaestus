package de.ibikus.appa.hephaestus.template.greeting.domain;

/** Нарушение инварианта адресата. Доменное исключение: адаптер решает, как его показать наружу. */
public class InvalidRecipientException extends RuntimeException {

    public InvalidRecipientException(String message) {
        super(message);
    }
}
