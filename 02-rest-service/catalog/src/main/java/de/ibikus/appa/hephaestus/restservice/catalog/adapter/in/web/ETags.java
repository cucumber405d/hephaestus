package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

/** Entity versions as strong HTTP entity tags. */
final class ETags {

    private ETags() {}

    static String of(long version) {
        return "\"" + version + "\"";
    }
}
