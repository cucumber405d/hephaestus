package de.ibikus.appa.hephaestus.security.ordering;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import de.ibikus.appa.hephaestus.security.ordering.adapter.in.web.RealmRolesConverter;
import java.util.List;
import java.util.Map;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Authenticated requests as the real converter sees them: subject plus realm roles in realm_access.roles. */
public final class TestTokens {

    public static final String ALICE = "a11ce000-0000-4000-8000-000000000001";
    public static final String CAROL = "ca201000-0000-4000-8000-000000000003";

    private TestTokens() {}

    public static RequestPostProcessor user(String subject, String... roles) {
        return jwt().jwt(token -> token.subject(subject).claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new RealmRolesConverter());
    }

    public static RequestPostProcessor alice() {
        return user(ALICE, "customer");
    }

    /** A second customer, to prove orders are private. */
    public static RequestPostProcessor carol() {
        return user(CAROL, "customer");
    }

    public static RequestPostProcessor bob() {
        return user("b0b00000-0000-4000-8000-000000000002", "smith");
    }

    public static RequestPostProcessor admin() {
        return user("ad000000-0000-4000-8000-000000000004", "admin");
    }
}
