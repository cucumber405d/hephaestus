package de.ibikus.appa.hephaestus.security.inventory.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class RealmRolesConverterTest {

    private final RealmRolesConverter converter = new RealmRolesConverter();

    @Test
    void realmRolesBecomeRoleAuthorities() {
        var jwt = token(Map.of("realm_access", Map.of("roles", List.of("customer", "inventory-reserver"))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_customer", "ROLE_inventory-reserver");
    }

    @Test
    void missingOrMalformedClaimMeansNoRoles() {
        assertThat(converter.convert(token(Map.of("other", "x")))).isEmpty();
        assertThat(converter.convert(token(Map.of("realm_access", "not-an-object"))))
                .isEmpty();
        assertThat(converter.convert(token(Map.of("realm_access", Map.of("roles", "admin")))))
                .isEmpty();
    }

    private static Jwt token(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("t").header("alg", "none").subject("someone");
        claims.forEach(builder::claim);
        return builder.build();
    }
}
