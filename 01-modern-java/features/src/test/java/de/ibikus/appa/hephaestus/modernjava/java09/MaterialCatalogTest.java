package de.ibikus.appa.hephaestus.modernjava.java09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MaterialCatalogTest {

    @Test
    void containsStandardMaterials() {
        assertThat(MaterialCatalog.standard()).containsOnlyKeys("BRZ", "IRN", "STL");
        assertThat(MaterialCatalog.standard().get("BRZ"))
                .isEqualTo(new Material("BRZ", "Bronze", new BigDecimal("12.50")));
    }

    @Test
    void catalogIsImmutable() {
        assertThatThrownBy(() -> MaterialCatalog.standard().put("X", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
