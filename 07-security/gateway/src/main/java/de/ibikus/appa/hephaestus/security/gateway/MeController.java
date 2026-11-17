package de.ibikus.appa.hephaestus.security.gateway;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Who is logged in: the first call of a frontend after login; it also hands out the XSRF-TOKEN cookie. */
@RestController
class MeController {

    @GetMapping("/me")
    Me me(@AuthenticationPrincipal OidcUser user, CsrfToken csrf) {
        // Reading the (deferred) token makes the repository write the XSRF-TOKEN cookie with this response
        csrf.getToken();
        return new Me(user.getSubject(), user.getPreferredUsername());
    }

    record Me(String subject, String name) {}
}
