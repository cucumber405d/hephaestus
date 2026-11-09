package de.ibikus.appa.hephaestus.restservice.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchTest {

    @Test
    void parsesSortOrders() {
        assertThat(SortOrder.parse("-price")).isEqualTo(new SortOrder(SortField.PRICE, true));
        assertThat(SortOrder.parse("name")).isEqualTo(new SortOrder(SortField.NAME, false));
        assertThat(SortOrder.parse("createdAt")).isEqualTo(new SortOrder(SortField.CREATED_AT, false));
    }

    @Test
    void rejectsUnknownSortField() {
        assertThatThrownBy(() -> SortOrder.parse("weight")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> SortOrder.parse("-")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void criteriaRejectMinAboveMax() {
        assertThatThrownBy(() -> new ItemSearchCriteria(null, new BigDecimal("10"), new BigDecimal("5"), null, null))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void criteriaSortByNameByDefault() {
        assertThat(new ItemSearchCriteria(null, null, null, null, null).sort())
                .containsExactly(new SortOrder(SortField.NAME, false));
        assertThat(new ItemSearchCriteria(null, null, null, null, List.of()).sort())
                .containsExactly(new SortOrder(SortField.NAME, false));
    }

    @Test
    void pageRequestBounds() {
        assertThatThrownBy(() -> new PageRequest(-1, 20)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new PageRequest(0, 0)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new PageRequest(0, 101)).isInstanceOf(InvalidValueException.class);
        assertThat(new PageRequest(0, 100).size()).isEqualTo(100);
    }

    @Test
    void totalPagesRoundsUp() {
        assertThat(new Page<>(List.of(), 0, 20, 41).totalPages()).isEqualTo(3);
        assertThat(new Page<>(List.of(), 0, 20, 40).totalPages()).isEqualTo(2);
        assertThat(new Page<>(List.of(), 0, 20, 0).totalPages()).isZero();
    }
}
