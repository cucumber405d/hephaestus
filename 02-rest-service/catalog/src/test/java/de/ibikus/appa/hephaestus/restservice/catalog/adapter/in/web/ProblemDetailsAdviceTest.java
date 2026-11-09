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
}
