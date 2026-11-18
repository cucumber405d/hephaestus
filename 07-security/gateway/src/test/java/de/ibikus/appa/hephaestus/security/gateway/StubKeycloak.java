package de.ibikus.appa.hephaestus.security.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Just enough of Keycloak for the BFF login: a key set and a token endpoint that answers the authorization code
 * (and refresh token) grant with a signed ID token. The authorization endpoint itself is never called: the test
 * plays the browser and the user, reads the redirect to it and comes back with a code.
 */
final class StubKeycloak implements AutoCloseable {

    record TokenRequest(Map<String, String> form, String authorization) {}

    private record Login(String subject, String username, String nonce) {}

    private final HttpServer server;
    private final RSAKey key;
    private final AtomicInteger issued = new AtomicInteger();
    private volatile Login nextLogin;
    private volatile String issuerOverride;
    private volatile TokenRequest lastTokenRequest;

    StubKeycloak() {
        try {
            key = new RSAKeyGenerator(2048).keyID("stub-key").generate();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/certs", exchange -> respond(exchange, 200, new JWKSet(key.toPublicJWK()).toString()));
        server.createContext("/token", this::token);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    String issuer() {
        return url() + "/realms/hephaestus";
    }

    /** The user who "logs in" next, and the nonce from the authorization request the ID token must echo. */
    void nextLogin(String subject, String username, String nonce) {
        nextLogin = new Login(subject, username, nonce);
    }

    /** Sign the next ID tokens as if another issuer had made them (null: the right one). */
    void issueAs(String issuer) {
        issuerOverride = issuer;
    }

    TokenRequest lastTokenRequest() {
        return lastTokenRequest;
    }

    static String accessTokenOf(String subject) {
        return "access-for-" + subject;
    }

    private void token(HttpExchange exchange) throws IOException {
        var form = Arrays.stream(
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> decode(pair[0]), pair -> pair.length > 1 ? decode(pair[1]) : "", (a, b) -> b));
        lastTokenRequest = new TokenRequest(form, exchange.getRequestHeaders().getFirst("Authorization"));
        var login = nextLogin;
        if (login == null) {
            respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        var n = issued.incrementAndGet();
        respond(exchange, 200, """
                {"access_token":"%s","token_type":"Bearer","expires_in":300,"refresh_token":"refresh-%d",
                 "scope":"openid profile email","id_token":"%s"}""".formatted(accessTokenOf(login.subject()), n, idToken(login)));
    }

    private String idToken(Login login) {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuerOverride != null ? issuerOverride : issuer())
                .subject(login.subject())
                .audience("shop-gateway")
                .claim("azp", "shop-gateway")
                .claim("nonce", login.nonce())
                .claim("preferred_username", login.username())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
