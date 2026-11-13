package de.ibikus.appa.hephaestus.containersci.catalog.adapter.out.persistence;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * Tells which database constraint a write violated, so that adapters translate only the violations they expect
 * and let every other one surface as an error.
 */
final class Constraints {

    private Constraints() {}

    static boolean violated(DataIntegrityViolationException e, String constraint) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            // Fallback for drivers or paths that do not extract the name: PostgreSQL quotes it in the message
            if (cause.getMessage() != null && cause.getMessage().contains("\"" + constraint + "\"")) {
                return true;
            }
        }
        return false;
    }
}
