package de.ibikus.appa.hephaestus.security.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The real gateway on a random port: a scripted browser logs in through the whole Authorization Code + PKCE flow
 * against a stub Keycloak, then uses the API through stub backends.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayBffIT {

    static final StubBackend ORDERING = new StubBackend("ordering");
    static final StubBackend INVENTORY = new StubBackend("inventory");
    static final StubKeycloak KEYCLOAK = new StubKeycloak();
    static final String ORDER_JSON = "{\"lines\":[{\"sku\":\"ANVIL-01\",\"quantity\":1}]}";

    @DynamicPropertySource
    static void backendsAndKeycloak(DynamicPropertyRegistry registry) {
        registry.add("ORDERING_URL", ORDERING::url);
        registry.add("INVENTORY_URL", INVENTORY::url);
        registry.add("OAUTH2_ISSUER", KEYCLOAK::issuer);
        registry.add("OAUTH2_AUTHORIZATION_URI", () -> KEYCLOAK.url() + "/auth");
        registry.add("OAUTH2_TOKEN_URI", () -> KEYCLOAK.url() + "/token");
        registry.add("OAUTH2_JWK_SET_URI", () -> KEYCLOAK.url() + "/certs");
        registry.add("SHOP_GATEWAY_CLIENT_SECRET", () -> "test-secret");
    }

    @AfterAll
    static void stopStubs() {
        ORDERING.close();
        INVENTORY.close();
        KEYCLOAK.close();
    }

    @AfterEach
    void rightIssuerAgain() {
        KEYCLOAK.issueAs(null);
    }

    @Value("${local.server.port}")
    int port;

    // --- anonymous

    @Test
    void anonymousApiCallIs401AndReachesNoBackend() {
        var before = ORDERING.lastRequest();

        var response = new Browser().get(uri("/api/orders/42"));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(ORDERING.lastRequest()).isSameAs(before);
    }

    @Test
    void anonymousFetchWithoutHtmlAcceptIs401() {
        // oauth2Login redirects only requests that accept HTML; everything else gets the first entry point (401)
        assertThat(new Browser().get(uri("/me")).statusCode()).isEqualTo(401);
    }

    @Test
    void anonymousPageIsSentToTheLogin() {
        var response = new Browser().navigate(uri("/me"));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
                .hasValueSatisfying(location -> assertThat(location).endsWith("/oauth2/authorization/keycloak"));
    }

    @Test
    void healthIsOpenForProbes() {
        assertThat(new Browser().get(uri("/actuator/health/liveness")).statusCode())
                .isEqualTo(200);
    }

    // --- login

    @Test
    void loginIsAuthorizationCodeWithPkceS256() throws Exception {
        var browser = new Browser();
        var authorization = startLogin(browser);

        assertThat(authorization.get("response_type")).isEqualTo("code");
        assertThat(authorization.get("client_id")).isEqualTo("shop-gateway");
        assertThat(authorization.get("scope")).contains("openid");
        assertThat(authorization.get("code_challenge_method")).isEqualTo("S256");
        assertThat(authorization.get("state")).isNotBlank();
        assertThat(authorization.get("nonce")).isNotBlank();

        finishLogin(browser, authorization, "alice-sub", "alice");

        var token = KEYCLOAK.lastTokenRequest();
        assertThat(token.form())
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "test-code");
        // The verifier only the gateway knew matches the challenge sent through the browser
        assertThat(s256(token.form().get("code_verifier"))).isEqualTo(authorization.get("code_challenge"));
        // Confidential client: authenticated with its secret (client_secret_basic)
        assertThat(token.authorization())
                .isEqualTo("Basic "
                        + Base64.getEncoder()
                                .encodeToString("shop-gateway:test-secret".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void meShowsTheUserAndHandsOutTheCsrfCookie() {
        var browser = login("alice-sub", "alice");

        var me = browser.get(uri("/me"));

        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"name\":\"alice\"").contains("\"subject\":\"alice-sub\"");
        assertThat(browser.cookie("XSRF-TOKEN")).isPresent();
        // The session cookie must be out of reach for JavaScript
        assertThat(browser.cookieDetails("JSESSIONID"))
                .hasValueSatisfying(c -> assertThat(c.isHttpOnly()).isTrue());
    }

    @Test
    void idTokenFromAnotherIssuerIsRejected() {
        KEYCLOAK.issueAs("http://evil.example/realms/hephaestus");
        var browser = new Browser();
        var callback = finishLogin(browser, startLogin(browser), "mallory-sub", "mallory");

        assertThat(callback.headers().firstValue("Location"))
                .hasValueSatisfying(location -> assertThat(location).contains("error"));
        assertThat(browser.get(uri("/api/orders/42")).statusCode()).isEqualTo(401);
    }

    // --- API through the gateway

    @Test
    void ordersGoToOrderingWithTheUsersTokenAndWithoutCookies() {
        var browser = login("alice-sub", "alice");

        var response = browser.get(uri("/api/orders/42"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ordering");
        assertThat(response.headers().firstValue("X-Gateway")).contains("hephaestus");
        var received = ORDERING.lastRequest();
        assertThat(received.path()).isEqualTo("/api/orders/42");
        assertThat(received.authorization()).isEqualTo("Bearer " + StubKeycloak.accessTokenOf("alice-sub"));
        assertThat(received.cookie()).isNull();
    }

    @Test
    void postWithoutCsrfHeaderIs403AndReachesNoBackend() {
        var browser = login("alice-sub", "alice");
        browser.get(uri("/me"));
        var before = ORDERING.lastRequest();

        var response = browser.post(uri("/api/orders"), ORDER_JSON, false);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(ORDERING.lastRequest()).isSameAs(before);
    }

    @Test
    void postWithCsrfHeaderIsForwardedWithItsBody() {
        var browser = login("alice-sub", "alice");
        browser.get(uri("/me"));

        var response = browser.post(uri("/api/orders"), ORDER_JSON, true);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(ORDERING.lastRequest().method()).isEqualTo("POST");
        assertThat(ORDERING.lastRequest().body()).isEqualTo(ORDER_JSON);
        assertThat(ORDERING.lastRequest().authorization()).startsWith("Bearer ");
    }

    @Test
    void stockGoesToInventoryWithTheApiVersionHeaderAndToken() {
        var browser = login("bob-sub", "bob");

        var response = browser.get(uri("/api/stock/ANVIL-01"), "2");

        assertThat(response.body()).isEqualTo("inventory");
        assertThat(INVENTORY.lastRequest().path()).isEqualTo("/api/stock/ANVIL-01");
        assertThat(INVENTORY.lastRequest().apiVersion()).isEqualTo("2");
        assertThat(INVENTORY.lastRequest().authorization())
                .isEqualTo("Bearer " + StubKeycloak.accessTokenOf("bob-sub"));
    }

    @Test
    void internalReservationApiIsNotExposedEvenWhenLoggedIn() {
        var browser = login("alice-sub", "alice");
        browser.get(uri("/me"));
        var before = INVENTORY.lastRequest();

        var response = browser.post(uri("/api/reservations"), "{}", true);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(INVENTORY.lastRequest()).isSameAs(before);
    }

    @Test
    void logoutEndsTheSession() {
        var browser = login("alice-sub", "alice");
        browser.get(uri("/me"));

        assertThat(browser.post(uri("/logout"), "", true).statusCode()).isEqualTo(200);

        assertThat(browser.get(uri("/api/orders/42")).statusCode()).isEqualTo(401);
    }

    // --- the scripted login

    private Browser login(String subject, String username) {
        var browser = new Browser();
        var callback = finishLogin(browser, startLogin(browser), subject, username);
        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(callback.headers().firstValue("Location"))
                .hasValueSatisfying(location -> assertThat(location).doesNotContain("error"));
        return browser;
    }

    /** Step 1: the gateway sends the browser to Keycloak; returns the parameters of that redirect. */
    private Map<String, String> startLogin(Browser browser) {
        var start = browser.get(uri("/oauth2/authorization/keycloak"));
        assertThat(start.statusCode()).isEqualTo(302);
        var location = URI.create(start.headers().firstValue("Location").orElseThrow());
        assertThat(location.toString()).startsWith(KEYCLOAK.url() + "/auth?");
        return query(location);
    }

    /** Step 2: the user logged in at "Keycloak", which sends the browser back with a code. */
    private HttpResponse<String> finishLogin(
            Browser browser, Map<String, String> authorization, String subject, String username) {
        KEYCLOAK.nextLogin(subject, username, authorization.get("nonce"));
        return browser.get(URI.create(authorization.get("redirect_uri") + "?code=test-code&state="
                + URLEncoder.encode(authorization.get("state"), StandardCharsets.UTF_8)));
    }

    private static Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    private static String s256(String verifier) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
