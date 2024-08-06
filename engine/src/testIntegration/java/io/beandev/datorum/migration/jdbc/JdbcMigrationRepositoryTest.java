package io.beandev.datorum.migration.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.beandev.datorum.CreateDatabase;
import io.beandev.datorum.migration.Difference;
import io.beandev.datorum.migration.Migration;
import io.beandev.datorum.migration.Scope;
import io.beandev.datorum.schema.jdbc.JdbcSchemaRepository;

//@ExtendWith(TestSuiteImpl.class)
@Tag("MyTestSuite")
public class JdbcMigrationRepositoryTest {
    private DataSource dataSource;
    private JdbcMigrationRepository jdbcMigrationRepository;
    private JdbcSchemaRepository jdbcSchemaRepository;

    @BeforeEach
    public void setup() throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:postgresql://127.0.0.1:32543/eventstore_db");
        hikariConfig.setUsername("postgres");
        hikariConfig.setPassword("password");

        HikariDataSource cp = new HikariDataSource(hikariConfig);
        cp.setMaximumPoolSize(12);
        cp.setMinimumIdle(2);
        dataSource = cp;

        // Try to open the connection of data source
        testConnection(dataSource);

        // Cleanup the schema before running tests
        cleanupSchema(dataSource);

        // Create Schema before test
        jdbcSchemaRepository = new JdbcSchemaRepository(dataSource);
        jdbcSchemaRepository.createBaseTables();

        jdbcMigrationRepository = new JdbcMigrationRepository(dataSource);

    }

    @AfterEach
    public void tearDown() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    private void testConnection(DataSource dataSource) {
        try (Connection testConnection = dataSource.getConnection()) {
            if (testConnection != null) {
                System.out.println("HikariCP configuration is valid and connection is successful.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            fail("HikariCP configuration is invalid or connection failed: " + e.getMessage());
        }
    }

    private void cleanupSchema(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP SCHEMA IF EXISTS datorum_schema CASCADE");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            fail("Schema cleanup failed: " + e.getMessage());
        }
    }

    @Test
    public void testJdbcSchemaRepositoryCreation() {
        try {
            assertNotNull(jdbcMigrationRepository, "jdbcMigrationRepository should be successfully instantiated");
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception occurred while testing jdbcMigrationRepository creation: " + e.getMessage());
        }
    }

    @Test
    void testTablesExist() {
        // Create table migration and difference
        jdbcMigrationRepository.createBaseTables();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Check for 'migration' table
            try (ResultSet tables = metaData.getTables(null, "datorum_schema", "migration", null)) {
                assertTrue(tables.next(), "Table 'migration' should exist");
            }

            // Check for 'difference' table
            try (ResultSet tables = metaData.getTables(null, "datorum_schema", "difference", null)) {
                assertTrue(tables.next(), "Table 'difference' should exist");
            }
        } catch (SQLException e) {
            fail("Exception occurred while checking the database", e);
        }
    }

    @Test
    void testSaveMigration() {
        // Create table migration and difference
        jdbcMigrationRepository.createBaseTables();

        // Create difference and migration object
        Difference difference = new Difference(0, "test", Scope.AGGREGATE);

        Migration schemaMigration = new Migration(
                1,
                20240715123711L,
                new Difference[] {
                        difference
                });
        // Save into tables
        jdbcMigrationRepository.save(schemaMigration);

        // Verify data difference insertion
        try (Connection conn = dataSource.getConnection()) {
            String selectSql = "SELECT * FROM datorum_schema.difference";
            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next(), "Row should exist");
                    // Verify the value of difference object
                    assertEquals(difference.id(), rs.getLong("id"), "ID should exist");
                    assertEquals(difference.name(), rs.getString("name"), "Name should exist");
                    assertEquals(difference.scope().name(), rs.getString("scope"), "Scope should exist");
                    assertEquals(difference.action().name(), rs.getString("action"), "Action should exist");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Assertions.fail("Exception occurred while testing data insertion: " + e.getMessage());
        }

        // Verify data migration insertion
        try (Connection conn = dataSource.getConnection()) {
            String selectSql = "SELECT * FROM datorum_schema.migration";
            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next(), "Row should exist");
                    // Verify the value of migration object
                    assertEquals(schemaMigration.parentId(), rs.getLong("parent_id"), "Parent ID should exist");
                    assertEquals(schemaMigration.id(), rs.getLong("id"), "ID should exist");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Assertions.fail("Exception occurred while testing data insertion: " + e.getMessage());
        }
    }

}
