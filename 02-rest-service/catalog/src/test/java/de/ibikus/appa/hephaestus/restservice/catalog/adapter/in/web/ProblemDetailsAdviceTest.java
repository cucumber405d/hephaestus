package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class ProblemDetailsAdviceTest {

    /** A concurrent update that slipped between the If-Match check and the flush is still a 412. */
    @Test
    void optimisticLockFailureIsPreconditionFailed() {
        var problem = new ProblemDetailsAdvice()
                .optimisticLock(new ObjectOptimisticLockingFailureException("ItemJpaEntity", "id"));

        assertThat(problem.getStatus()).isEqualTo(412);
        assertThat(problem.getType().toString()).endsWith("/precondition-failed");
    }

    /** Anything unexpected is still Problem Details, and its message never leaks to the client. */
    @Test
    void unexpectedExceptionIsAnInternalErrorWithoutDetails() {
        var problem = new ProblemDetailsAdvice().unexpected(new IllegalStateException("secret connection string"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getType()).isNotNull();
        assertThat(problem.getType().toString()).endsWith("/internal-error");
        assertThat(problem.getDetail()).isNotNull().doesNotContain("secret");
    }
}
