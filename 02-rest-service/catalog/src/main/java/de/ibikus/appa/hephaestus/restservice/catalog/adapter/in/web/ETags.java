package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.VersionConflictException;
import java.util.regex.Pattern;

/** Entity versions as strong HTTP entity tags ({@code "3"}). */
final class ETags {

    private static final Pattern STRONG_NUMERIC = Pattern.compile("\"(\\d{1,18})\"");

    private ETags() {}

    static String of(long version) {
        return "\"" + version + "\"";
    }

    /**
     * Accepts exactly one strong numeric entity tag. Wildcards, weak tags and lists are refused with 412: for an
     * update they could only mean "overwrite whatever is there", which is what If-Match exists to prevent.
     */
    static long parseIfMatch(String header) {
        var matcher = STRONG_NUMERIC.matcher(header == null ? "" : header.strip());
        if (!matcher.matches()) {
            throw new VersionConflictException("If-Match must be one strong entity tag such as \"3\", got: " + header);
        }
        return Long.parseLong(matcher.group(1));
    }
}
