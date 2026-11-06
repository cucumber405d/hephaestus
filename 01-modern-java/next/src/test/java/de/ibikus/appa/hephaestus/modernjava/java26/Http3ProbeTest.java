package de.ibikus.appa.hephaestus.modernjava.java26;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Needs the public internet and UDP/443; excluded by default, run with -Dgroups=network. */
@Tag("network")
class Http3ProbeTest {

    @Test
    void fetchesAPageWithHttp3Requested() throws Exception {
        var response = Http3Probe.fetch(URI.create("https://cloudflare-quic.com/"));

        assertThat(response.statusCode()).isEqualTo(200);
        // 200 alone would also pass after a silent fallback to HTTP/2
        assertThat(response.version()).isEqualTo(java.net.http.HttpClient.Version.HTTP_3);
    }
}
