package de.ibikus.appa.hephaestus.security.inventory.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.Map;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Authenticated requests as the real converter sees them: realm roles in realm_access.roles. */
final class TestTokens {

    private TestTokens() {}

    static RequestPostProcessor withRoles(String subject, String... roles) {
        return jwt().jwt(token -> token.subject(subject).claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new RealmRolesConverter());
    }

    static RequestPostProcessor orderingService() {
        return withRoles("service-account-ordering", "inventory-reserver");
    }

    static RequestPostProcessor customer() {
        return withRoles("alice", "customer");
    }
}
