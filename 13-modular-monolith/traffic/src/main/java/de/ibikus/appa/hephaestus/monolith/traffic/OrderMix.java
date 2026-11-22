package de.ibikus.appa.hephaestus.monolith.traffic;

/** Custom orders, 45 % IRON-01, 45 % STEEL-01, 10 % MITHRIL-01 (never deliverable: expected FAILED). */
record OrderMix(String material, String expected) {

    static OrderMix of(long i) {
        var slot = (int) (i % 20);
        if (slot < 9) {
            return new OrderMix("IRON-01", "DONE");
        }
        if (slot < 18) {
            return new OrderMix("STEEL-01", "DONE");
        }
        return new OrderMix("MITHRIL-01", "FAILED");
    }

    String json(long i) {
        return "{\"customerId\":\"cutover-" + i + "\",\"custom\":true,\"material\":\"" + material
                + "\",\"quantity\":1}";
    }
}
