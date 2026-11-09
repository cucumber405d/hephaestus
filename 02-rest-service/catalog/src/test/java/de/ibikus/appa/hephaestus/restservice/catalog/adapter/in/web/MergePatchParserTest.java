package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.PatchValue;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.InvalidValueException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MergePatchParserTest {

    private static Map<String, Object> body(Object... keyValues) {
        var map = new HashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void absentKeysKeepTheirValues() {
        var patch = MergePatchParser.parse(body("name", "Axe"));

        assertThat(patch.name()).isEqualTo(PatchValue.set("Axe"));
        assertThat(patch.description()).isEqualTo(PatchValue.absent());
        assertThat(patch.priceAmount()).isEqualTo(PatchValue.absent());
        assertThat(patch.categoryId()).isEqualTo(PatchValue.absent());
    }

    @Test
    void nullDescriptionRemovesIt() {
        assertThat(MergePatchParser.parse(body("description", null)).description())
                .isEqualTo(PatchValue.remove());
    }

    @Test
    void priceIsMergedFieldByField() {
        var patch = MergePatchParser.parse(body("price", Map.of("amount", "9.99")));

        assertThat(patch.priceAmount()).isEqualTo(PatchValue.set("9.99"));
        assertThat(patch.priceCurrency()).isEqualTo(PatchValue.absent());
    }

    /** The contract says amount is a string; a JSON number would bypass the format check (e.g. 1E+999999999). */
    @Test
    void numericAmountIsRejected() {
        assertThatThrownBy(() -> MergePatchParser.parse(body("price", Map.of("amount", 12.5))))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void categoryIdIsParsed() {
        var id = UUID.randomUUID();

        assertThat(MergePatchParser.parse(body("categoryId", id.toString())).categoryId())
                .isEqualTo(PatchValue.set(new CategoryId(id)));
    }

    @Test
    void requiredFieldsCannotBeRemoved() {
        assertThatThrownBy(() -> MergePatchParser.parse(body("name", null))).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> MergePatchParser.parse(body("price", null))).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> MergePatchParser.parse(body("categoryId", null)))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> MergePatchParser.parse(body("price", body("currency", null))))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void readOnlyAndUnknownKeysAreRejected() {
        assertThatThrownBy(() ->
                        MergePatchParser.parse(body("id", UUID.randomUUID().toString())))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> MergePatchParser.parse(body("createdAt", "2026-01-01T00:00:00Z")))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> MergePatchParser.parse(body("weight", 3))).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> MergePatchParser.parse(body("price", body("discount", "1"))))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void wrongTypesAreRejected() {
        assertThatThrownBy(() -> MergePatchParser.parse(body("name", 42))).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> MergePatchParser.parse(body("price", "9.99")))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> MergePatchParser.parse(body("categoryId", "not-a-uuid")))
                .isInstanceOf(InvalidValueException.class);
    }
}
