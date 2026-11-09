package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.ItemPatch;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.PatchValue;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.InvalidValueException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a JSON Merge Patch (RFC 7386) body into an {@link ItemPatch}. The body arrives as a map, so an absent key
 * (keep) and a {@code null} value (remove) stay distinguishable. Only description may be removed.
 */
final class MergePatchParser {

    private static final Set<String> FIELDS = Set.of("name", "description", "price", "categoryId");
    private static final Set<String> PRICE_FIELDS = Set.of("amount", "currency");

    private MergePatchParser() {}

    static ItemPatch parse(Map<String, Object> body) {
        if (body == null) {
            throw new InvalidValueException("Merge patch body must be a JSON object");
        }
        rejectUnknown(body, FIELDS, "");
        PatchValue<String> priceAmount = PatchValue.absent();
        PatchValue<String> priceCurrency = PatchValue.absent();
        if (body.containsKey("price")) {
            if (!(body.get("price") instanceof Map<?, ?> price)) {
                throw new InvalidValueException("price must be an object and cannot be removed");
            }
            rejectUnknown(price, PRICE_FIELDS, "price.");
            priceAmount = required(price, "amount", "price.amount", MergePatchParser::decimal);
            priceCurrency = required(price, "currency", "price.currency", MergePatchParser::text);
        }
        return new ItemPatch(
                required(body, "name", "name", MergePatchParser::text),
                optional(body, "description", MergePatchParser::text),
                priceAmount,
                priceCurrency,
                required(body, "categoryId", "categoryId", MergePatchParser::categoryId));
    }

    private interface Converter<T> {
        T convert(Object value, String field);
    }

    private static void rejectUnknown(Map<?, ?> map, Set<String> allowed, String prefix) {
        for (var key : map.keySet()) {
            if (!allowed.contains(String.valueOf(key))) {
                throw new InvalidValueException(
                        "Field '" + prefix + key + "' cannot be patched; allowed: " + String.join(", ", allowed));
            }
        }
    }

    private static <T> PatchValue<T> required(Map<?, ?> map, String key, String field, Converter<T> converter) {
        if (!map.containsKey(key)) {
            return PatchValue.absent();
        }
        var value = map.get(key);
        if (value == null) {
            throw new InvalidValueException(field + " is required and cannot be removed");
        }
        return PatchValue.set(converter.convert(value, field));
    }

    private static <T> PatchValue<T> optional(Map<?, ?> map, String key, Converter<T> converter) {
        if (!map.containsKey(key)) {
            return PatchValue.absent();
        }
        var value = map.get(key);
        return value == null ? PatchValue.remove() : PatchValue.set(converter.convert(value, key));
    }

    private static String text(Object value, String field) {
        if (value instanceof String text) {
            return text;
        }
        throw new InvalidValueException(field + " must be a string");
    }

    private static String decimal(Object value, String field) {
        if (value instanceof String || value instanceof Number) {
            return value.toString();
        }
        throw new InvalidValueException(field + " must be a decimal number");
    }

    private static CategoryId categoryId(Object value, String field) {
        try {
            return new CategoryId(UUID.fromString(text(value, field)));
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException(field + " must be a UUID");
        }
    }
}
