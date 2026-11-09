package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.IdempotencyStore;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.StoredResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Makes {@code POST /api/items} idempotent by the {@code Idempotency-Key} header. The key is reserved before the
 * request runs, so concurrent duplicates cannot both create an item: they get 409 while the first one is in
 * progress, or the stored response afterwards. Only 2xx responses are stored; any other outcome releases the key so
 * the client can retry. The same key with a different body is a 409 conflict.
 */
@Component
class IdempotencyFilter extends OncePerRequestFilter {

    static final String HEADER = "Idempotency-Key";
    static final int MAX_KEY_LENGTH = 100;

    private final IdempotencyStore store;
    private final Clock clock;

    IdempotencyFilter(IdempotencyStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/api/items".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var key = request.getHeader(HEADER);
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            problem(
                    request,
                    response,
                    400,
                    "invalid-request",
                    "Invalid request",
                    HEADER + " header is required (1.." + MAX_KEY_LENGTH + " characters)");
            return;
        }

        var body = request.getInputStream().readAllBytes();
        var hash = sha256(body);

        if (!store.reserve(key, hash, clock.instant())) {
            answerExisting(request, response, key, hash);
            return;
        }

        var wrapper = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            chain.doFilter(new CachedBodyRequest(request, body), wrapper);
            int status = wrapper.getStatus();
            if (status >= 200 && status < 300) {
                store.complete(
                        key,
                        new StoredResponse(
                                hash,
                                status,
                                new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8),
                                wrapper.getHeader(HttpHeaders.LOCATION),
                                wrapper.getHeader(HttpHeaders.ETAG)));
                stored = true;
            }
        } finally {
            if (!stored) {
                store.release(key);
            }
            wrapper.copyBodyToResponse();
        }
    }

    private void answerExisting(HttpServletRequest request, HttpServletResponse response, String key, String hash)
            throws IOException {
        var existing = store.find(key);
        if (existing.isEmpty() || !existing.get().completed()) {
            problem(
                    request,
                    response,
                    409,
                    "idempotency-conflict",
                    "Idempotency conflict",
                    "A request with this " + HEADER + " is still in progress");
        } else if (!existing.get().requestHash().equals(hash)) {
            problem(
                    request,
                    response,
                    409,
                    "idempotency-conflict",
                    "Idempotency conflict",
                    HEADER + " was already used with a different request body");
        } else {
            replay(response, existing.get());
        }
    }

    private static void replay(HttpServletResponse response, StoredResponse stored) throws IOException {
        response.setStatus(stored.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (stored.location() != null) {
            response.setHeader(HttpHeaders.LOCATION, stored.location());
        }
        if (stored.etag() != null) {
            response.setHeader(HttpHeaders.ETAG, stored.etag());
        }
        response.setHeader("Idempotent-Replayed", "true");
        if (stored.body() != null) {
            response.getOutputStream().write(stored.body().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Problem Details written directly: the filter runs before Spring MVC's exception handling. */
    private static void problem(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String slug,
            String title,
            String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        var json = """
                {"type":"%s%s","title":"%s","status":%d,"detail":"%s","instance":"%s"}""".formatted(ProblemDetailsAdvice.TYPE_BASE, slug, title, status, detail, request.getRequestURI());
        response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /** The body was consumed for hashing; this wrapper lets the rest of the chain read it again. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Async reads are not supported");
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return in.read(buffer, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
