package de.ibikus.appa.hephaestus.containersci.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresTestcontainers.class)
class SchemaMigrationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void seedsThreeCategories() {
        var names = jdbc.queryForList("select name from category order by name", String.class);

        assertThat(names).containsExactly("Armor", "Tools", "Weapons");
    }

    @Test
    void migratesItemTable() {
        var constraints = jdbc.queryForList(
                "select constraint_name from information_schema.table_constraints where table_name = 'item'",
                String.class);

        assertThat(constraints).contains("item_category_fk");
    }
}
