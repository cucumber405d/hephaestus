package de.ibikus.appa.hephaestus.monolith.traffic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class GeneratorTest {

    @Test
    void acceptedOrdersGoToTheCsvWithLfAndTheirExpectedOutcome() throws Exception {
        try (var monolith = new FakeServer()
                .otherwise(202, "{\"id\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"}")) {
            var csv = new StringWriter();
            var generator = new Generator(new Http(null, 2), monolith.url(), csv);
            assertThat(generator.place(0)).isTrue();
            assertThat(generator.place(18)).isTrue();
            assertThat(csv.toString())
                    .isEqualTo("3fa85f64-5717-4562-b3fc-2c963f66afa6,IRON-01,DONE\n"
                            + "3fa85f64-5717-4562-b3fc-2c963f66afa6,MITHRIL-01,FAILED\n")
                    .doesNotContain("\r");
        }
    }

    @Test
    void aRefusedOrderIsNotInTheCsv() throws Exception {
        try (var monolith = new FakeServer().otherwise(400, "{\"error\":\"bad\"}")) {
            var csv = new StringWriter();
            assertThat(new Generator(new Http(null, 2), monolith.url(), csv).place(0))
                    .isFalse();
            assertThat(csv.toString()).isEmpty();
        }
    }

    @Test
    void theMixIsFortyFiveFortyFiveTen() {
        var mithril = 0;
        for (long i = 0; i < 200; i++) {
            if (OrderMix.of(i).material().equals("MITHRIL-01")) {
                mithril++;
                assertThat(OrderMix.of(i).expected()).isEqualTo("FAILED");
            }
        }
        assertThat(mithril).isEqualTo(20);
    }
}
