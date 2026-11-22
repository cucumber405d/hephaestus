package de.ibikus.appa.hephaestus.monolith.traffic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmithBotTest {

    static final String LIST = """
            [{"id":"WO-000001","status":"PLANNED","origin":"MONOLITH"},
             {"id":"FW-6f1c","status":"IN_PROGRESS","origin":"SERVICE"},
             {"id":"FW-77aa","status":"DONE","origin":"SERVICE"}]""";

    @Test
    void routesByIdPrefixLikeTheHttpRoute() throws Exception {
        try (var monolith = new FakeServer();
                var service = new FakeServer().then(200, LIST)) {
            var bot = new SmithBot(new Http(null, 3), monolith.url(), service.url(), null);
            assertThat(bot.round()).isEqualTo(2);
            assertThat(monolith.requests).containsExactly("POST /api/work-orders/WO-000001/start");
            assertThat(service.requests)
                    .containsExactly("GET /api/work-orders", "POST /api/work-orders/FW-6f1c/complete");
        }
    }

    @Test
    void withAGatewayEverythingGoesThere() throws Exception {
        try (var gateway = new FakeServer().then(200, LIST)) {
            var bot = new SmithBot(new Http(null, 3), "http://unused", "http://unused", gateway.url());
            bot.round();
            assertThat(gateway.requests).hasSize(3);
        }
    }

    @Test
    void conflictIsAnAnswerNotAnError() throws Exception {
        try (var monolith = new FakeServer().otherwise(409, "");
                var service = new FakeServer().then(200, LIST).otherwise(409, "")) {
            var bot = new SmithBot(new Http(null, 3), monolith.url(), service.url(), null);
            assertThat(bot.round()).isZero();
            assertThat(monolith.requests).hasSize(1);
        }
    }

    @Test
    void retriesOn503() throws Exception {
        try (var monolith = new FakeServer().then(503, "").then(200, "");
                var service = new FakeServer()
                        .then(200, "[{\"id\":\"WO-000002\",\"status\":\"PLANNED\"}]")) {
            var bot = new SmithBot(new Http(null, 3), monolith.url(), service.url(), null);
            assertThat(bot.round()).isEqualTo(1);
            assertThat(monolith.requests).hasSize(2);
        }
    }

    @Test
    void parsesTheListWhateverTheOrderOfFields() {
        assertThat(SmithBot.parse("[{\"status\":\"PLANNED\",\"id\":\"FW-1\"}]"))
                .containsExactly(new SmithBot.Item("FW-1", "PLANNED"));
    }
}
