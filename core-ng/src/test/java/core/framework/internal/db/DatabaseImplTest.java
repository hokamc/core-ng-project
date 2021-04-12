package core.framework.internal.db;

import core.framework.db.Transaction;
import core.framework.db.UncheckedSQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author neo
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseImplTest {
    private DatabaseImpl database;

    @BeforeAll
    void createDatabase() {
        database = new DatabaseImpl("db");
        database.url("jdbc:hsqldb:mem:.;sql.syntax_mys=true");
        database.view(EntityView.class);

        database.execute("CREATE TABLE database_test (id INT PRIMARY KEY, string_field VARCHAR(20), enum_field VARCHAR(10), date_field DATE, date_time_field TIMESTAMP)");
    }

    @AfterAll
    void cleanupDatabase() {
        database.execute("DROP TABLE database_test");
    }

    @BeforeEach
    void truncateTable() {
        database.execute("TRUNCATE TABLE database_test");
    }

    @Test
    void selectOneWithView() {
        insertRow(1, "string1", TestEnum.V1);

        EntityView view = database.selectOne("SELECT string_field as string_label, enum_field as enum_label FROM database_test where id = ?", EntityView.class, 1).orElseThrow();

        assertThat(view.enumField).isEqualTo(TestEnum.V1);
        assertThat(view.stringField).isEqualTo("string1");
    }

    @Test
    void selectWithView() {
        insertRow(1, "string1", TestEnum.V1);
        insertRow(2, "string2", TestEnum.V2);

        List<EntityView> views = database.select("SELECT string_field as string_label, enum_field as enum_label FROM database_test", EntityView.class);

        assertThat(views).hasSize(2);
        assertThat(views.get(0).enumField).isEqualTo(TestEnum.V1);
        assertThat(views.get(1).enumField).isEqualTo(TestEnum.V2);
    }

    @Test
    void selectEmptyWithView() {
        List<EntityView> views = database.select("SELECT string_field as string_label, enum_field as enum_label FROM database_test where id = -1", EntityView.class);

        assertThat(views).isEmpty();
    }

    @Test
    void selectNullInt() {
        Optional<Integer> result = database.selectOne("SELECT max(id) FROM database_test", Integer.class);
        assertThat(result).isNotPresent();
    }

    @Test
    void selectNumber() {
        assertThat(database.selectOne("SELECT count(id) FROM database_test", Integer.class)).get().isEqualTo(0);
        assertThat(database.selectOne("SELECT count(id) FROM database_test", Long.class)).get().isEqualTo(0L);
        assertThat(database.selectOne("SELECT count(id) FROM database_test", Double.class)).get().isEqualTo(0.0);
        assertThat(database.selectOne("SELECT count(id) FROM database_test", BigDecimal.class)).get().isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void selectDate() {
        LocalDate date = LocalDate.of(2017, 11, 22);
        LocalDateTime dateTime = LocalDateTime.of(2017, 11, 22, 13, 0, 0);
        database.execute("INSERT INTO database_test (id, date_field, date_time_field) VALUES (?, ?, ?)", 1, date, dateTime);

        assertThat(database.selectOne("SELECT date_field FROM database_test where id = ?", LocalDate.class, 1)).get().isEqualTo(date);
        assertThat(database.selectOne("SELECT date_time_field FROM database_test where id = ?", LocalDateTime.class, 1)).get().isEqualTo(dateTime);
        assertThat(database.selectOne("SELECT date_time_field FROM database_test where id = ?", ZonedDateTime.class, 1).orElseThrow().toLocalDateTime()).isEqualTo(dateTime);
    }

    @Test
    void selectString() {
        insertRow(1, "string1", TestEnum.V1);

        Optional<String> result = database.selectOne("SELECT string_field FROM database_test", String.class);
        assertThat(result).get().isEqualTo("string1");
    }

    @Test
    void validateSQL() {
        assertThatThrownBy(() -> database.select("SELECT * FROM database_test", String.class))
                .isInstanceOf(Error.class)
                .hasMessageContaining("sql must not contain wildcard(*)");
        assertThatThrownBy(() -> database.selectOne("SELECT id FROM database_test WHERE string_field = 'value'", Integer.class))
                .isInstanceOf(Error.class)
                .hasMessageContaining("sql must not contain single quote(')");
        assertThatThrownBy(() -> database.execute("UPDATE database_test SET string_value = 'value' WHERE string_field = 'value'", Integer.class))
                .isInstanceOf(Error.class)
                .hasMessageContaining("sql must not contain single quote(')");
    }

    @Test
    void validateAsterisk() {
        database.validateAsterisk("select column * 10 from table");
        database.validateAsterisk("select 3*5, 4*2 from table");
        database.validateAsterisk("select 3 * ? from table");

        assertThatThrownBy(() -> database.validateAsterisk("select * from table")).isInstanceOf(Error.class);
        assertThatThrownBy(() -> database.validateAsterisk("select * from")).isInstanceOf(Error.class);
        assertThatThrownBy(() -> database.validateAsterisk("select t.* , t.column from table t")).isInstanceOf(Error.class);
        assertThatThrownBy(() -> database.validateAsterisk("select 3*4, * from table")).isInstanceOf(Error.class);
        assertThatThrownBy(() -> database.validateAsterisk("select *")).isInstanceOf(Error.class);
    }

    @Test
    void commitTransaction() {
        try (Transaction transaction = database.beginTransaction()) {
            insertRow(1, "string", TestEnum.V1);
            transaction.commit();
        }

        Optional<EntityView> result = database.selectOne("SELECT string_field as string_label, enum_field as enum_label FROM database_test where id = ?", EntityView.class, 1);
        assertThat(result).get().satisfies(view -> {
            assertThat(view.enumField).isEqualTo(TestEnum.V1);
            assertThat(view.stringField).isEqualTo("string");
        });
    }

    @Test
    void batchExecute() {
        insertRow(1, "string1", TestEnum.V1);
        insertRow(2, "string2", TestEnum.V2);

        List<Object[]> params = List.of(new Object[]{"string3", 1}, new Object[]{"string4", 2});
        int[] results = database.batchExecute("UPDATE database_test SET string_field = ? WHERE id = ?", params);

        assertThat(results).containsExactly(1, 1);
    }

    @Test
    void batchExecuteWithEmptyParams() {
        assertThatThrownBy(() -> database.batchExecute("UPDATE database_test SET string_field = ? WHERE id = ?", List.of()))
                .isInstanceOf(Error.class)
                .hasMessageContaining("params must not be empty");
    }

    @Test
    void duplicateKey() {
        String sql = "INSERT INTO database_test (id) VALUES (?)";

        database.execute(sql, 1);
        assertThatThrownBy(() -> database.execute(sql, 1))
                .isInstanceOf(UncheckedSQLException.class)
                .satisfies(e -> {
                    UncheckedSQLException exception = (UncheckedSQLException) e;
                    assertThat(exception.sqlSate).startsWith("23");
                    assertThat(exception.errorType).isEqualTo(UncheckedSQLException.ErrorType.INTEGRITY_CONSTRAINT_VIOLATION);
                });

        // the underlying db is hsql, hsql throws BatchUpdateException directly without SQLIntegrityConstraintViolationException as cause
        Object[] params = {1};
        assertThatThrownBy(() -> database.batchExecute(sql, List.of(params, params)))
                .isInstanceOf(UncheckedSQLException.class)
                .satisfies(e -> {
                    UncheckedSQLException exception = (UncheckedSQLException) e;
                    assertThat(exception.sqlSate).startsWith("23");
                    assertThat(exception.errorType).isEqualTo(UncheckedSQLException.ErrorType.INTEGRITY_CONSTRAINT_VIOLATION);
                });
    }

    @Test
    void rollbackTransaction() {
        try (Transaction transaction = database.beginTransaction()) {
            insertRow(1, "string", TestEnum.V1);
            transaction.rollback();
        }

        Optional<EntityView> result = database.selectOne("SELECT string_field as string_label, enum_field as enum_label FROM database_test where id = ?", EntityView.class, 1);
        assertThat(result).isNotPresent();
    }

    @Test
    void rollbackTransactionWithException() {
        assertThatThrownBy(() -> {
            try (Transaction transaction = database.beginTransaction()) {
                insertRow(1, "string", TestEnum.V1);
                database.execute("invalid sql");
                transaction.commit();
            }
        }).isInstanceOf(UncheckedSQLException.class);

        int count = database.selectOne("SELECT count(1) FROM database_test where id = ?", Integer.class, 1).orElse(0);
        assertThat(count).isZero();
    }

    @Test
    void driverProperties() {
        Properties properties = database.driverProperties("jdbc:mysql://localhost/demo", null, null);
        assertThat(properties)
                .doesNotContainKeys("user", "password")
                .containsEntry("useSSL", "false")
                .containsEntry("characterEncoding", "utf-8");

        properties = database.driverProperties("jdbc:mysql://localhost/demo?useSSL=true&characterEncoding=latin1", "user", "password");
        assertThat(properties).doesNotContainKeys("useSSL", "characterEncoding");

        properties = database.driverProperties("jdbc:mysql://localhost/demo?useSSL=true", null, null);
        assertThat(properties)
                .doesNotContainKeys("useSSL")
                .containsEntry("characterEncoding", "utf-8");

        properties = database.driverProperties("jdbc:postgresql://localhost/demo", null, null);
        assertThat(properties)
            .doesNotContainKeys("user", "password")
            .containsEntry("ssl", "false");

        properties = database.driverProperties("jdbc:mysql://localhost/demo?useSSL=true", null, null);
        assertThat(properties)
            .doesNotContainKeys("ssl");
    }

    private void insertRow(int id, String stringField, TestEnum enumField) {
        database.execute("INSERT INTO database_test (id, string_field, enum_field) VALUES (?, ?, ?)", id, stringField, enumField);
    }
}
