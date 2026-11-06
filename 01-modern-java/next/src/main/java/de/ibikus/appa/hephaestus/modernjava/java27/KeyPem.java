package de.ibikus.appa.hephaestus.modernjava.java27;

import java.security.PEMDecoder;
import java.security.PEMEncoder;
import java.security.PublicKey;

/**
 * PEM encodings of cryptographic objects (JEP 538, preview in Java 27): read and write the textual
 * {@code -----BEGIN ...-----} format without Bouncy Castle or manual Base64 handling.
 */
public final class KeyPem {

    private KeyPem() {}

    public static String toPem(PublicKey key) {
        return PEMEncoder.of().encodeToString(key);
    }

    public static PublicKey fromPem(String pem) {
        return PEMDecoder.of().decode(pem, PublicKey.class);
    }
}
