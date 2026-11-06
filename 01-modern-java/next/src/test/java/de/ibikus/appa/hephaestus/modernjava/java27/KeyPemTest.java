package de.ibikus.appa.hephaestus.modernjava.java27;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import org.junit.jupiter.api.Test;

class KeyPemTest {

    @Test
    void encodesPublicKeyAsPemAndBack() throws Exception {
        var key = KeyPairGenerator.getInstance("EC").generateKeyPair().getPublic();

        var pem = KeyPem.toPem(key);

        assertThat(pem).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(KeyPem.fromPem(pem)).isEqualTo(key);
    }
}
