package de.ibikus.appa.hephaestus.security.gateway;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/** A minimal browser: keeps cookies, does not follow redirects (the test decides where to go next). */
final class Browser {

    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    HttpResponse<String> get(URI uri) {
        return send(HttpRequest.newBuilder(uri).GET());
    }

    /** A page navigation: browsers send Accept: text/html, which is what selects the login redirect. */
    HttpResponse<String> navigate(URI uri) {
        return send(HttpRequest.newBuilder(uri).header("Accept", "text/html").GET());
    }

    HttpResponse<String> get(URI uri, String apiVersion) {
        return send(
                HttpRequest.newBuilder(uri).header("API-Version", apiVersion).GET());
    }

    /** A JSON POST; with csrf, the X-XSRF-TOKEN header carries the value of the XSRF-TOKEN cookie, as a SPA does. */
    HttpResponse<String> post(URI uri, String json, boolean csrf) {
        var request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (csrf) {
            request.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN").orElseThrow());
        }
        return send(request);
    }

    Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    Optional<HttpCookie> cookieDetails(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals(name))
                .findFirst();
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
