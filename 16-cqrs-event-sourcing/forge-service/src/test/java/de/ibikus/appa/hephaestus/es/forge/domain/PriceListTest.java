package de.ibikus.appa.hephaestus.es.forge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.es.forge.domain.PriceList.Rate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PriceListTest {

    private final PriceList prices = new PriceList(
            Map.of("IRON-01", new Rate(1200, 2), "STEEL-01", new Rate(2500, 3), "MITHRIL-01", new Rate(99000, 30)), 5);

    @Test
    void iron5Costs6000AndTakes3Days() {
        assertThat(prices.price("IRON-01", 5)).isEqualTo(new PriceList.Price(6000, 3));
    }

    @Test
    void steel11Takes6Days() {
        // 3 base days + ceil(11 / 5) = 3
        assertThat(prices.price("STEEL-01", 11)).isEqualTo(new PriceList.Price(27500, 6));
    }

    @Test
    void unknownMaterialThrows() {
        assertThatThrownBy(() -> prices.price("FOO", 1))
                .isInstanceOf(UnknownMaterial.class)
                .hasMessage("unknown material: FOO");
    }

    @Test
    void zeroQuantityThrows() {
        assertThatThrownBy(() -> prices.price("IRON-01", 0)).isInstanceOf(InvalidQuantity.class);
        assertThatThrownBy(() -> prices.price("IRON-01", -1)).isInstanceOf(InvalidQuantity.class);
    }
}
