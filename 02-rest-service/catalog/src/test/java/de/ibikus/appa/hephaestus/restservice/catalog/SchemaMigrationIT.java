package de.ibikus.appa.hephaestus.restservice.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresTestcontainers.class)
class SchemaMigrationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatesAllTables() {
        var tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tables).contains("category", "item", "idempotency_key", "flyway_schema_history");
    }

    @Test
    void categoryNamesAreUniqueIgnoringCase() {
        jdbc.update("insert into category (id, name, version) values (gen_random_uuid(), 'Swords', 0)");

        assertThatThrownBy(() ->
                        jdbc.update("insert into category (id, name, version) values (gen_random_uuid(), 'swords', 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void itemPriceMustNotBeNegative() {
        var categoryId = jdbc.queryForObject(
                "insert into category (id, name, version) values (gen_random_uuid(), 'Shields', 0) returning id",
                java.util.UUID.class);

        assertThatThrownBy(() -> jdbc.update(
                        "insert into item (id, name, price_amount, price_currency, category_id, created_at, version)"
                                + " values (gen_random_uuid(), 'Broken', -1, 'EUR', ?, now(), 0)",
                        categoryId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
