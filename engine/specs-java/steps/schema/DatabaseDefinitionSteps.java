package steps.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.beandev.datorum.CreatePostgres;
import io.beandev.datorum.schema.jdbc.JdbcSchemaRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;

public class DatabaseDefinitionSteps {
    private JdbcSchemaRepository jdbcSchemaRepository;
    private HikariDataSource dataSource;

    @Given("^a Postgres database without schemas$")
    public void aPostgresDatabaseWithoutSchemas() throws Exception {

        CreatePostgres.getInstance();
        dataSource = dataSource();

        String schemaName = "datorum_schema";
        dropSchemaIfExists(schemaName);
    }

    @And("an implementation of SchemaRepository")
    public void anImplementationOfSchemaRepository() {
        jdbcSchemaRepository = new JdbcSchemaRepository(dataSource);
    }

    @When("createBaseTables\\() is executed")
    public void createbasetablesIsExecuted() {
        jdbcSchemaRepository.createBaseTables();
    }

    @Then("schema {word} SHOULD be created")
    public void schemaShouldBeCreated(String schemaName) {
        checkSchemaExist(schemaName);
    }

    @And("table {word} SHOULD be created in schema {word}")
    public void tableShouldBeCreatedInSchema(String table, String schemaName) {
        verifyTableInSchema(table, schemaName);
    }

    @And("table {word} SHOULD have {word} column reference table {word}'s primary key")
    public void tableShouldHaveColumnReferenceOtherTablePrimaryKey(String table, String columnName,
            String referencedTable) {
        verifyTableHasColumnReferenceOtherTablePK(table, columnName, referencedTable);
    }

    @And("table {word} SHOULD have required {word}\\({int}\\) {word} column")
    public void tableShouldHaveRequiredVarcharTypeColumn(String tableName, String dataType,
            Integer length, String columnName) {
        verifyIsVarcharColumn(tableName, columnName, dataType, length);
    }

    @And("table {word} SHOULD have {word} {word} column")
    public void tableShouldHaveIntTypeColumn(String tableName, String dataType, String columnName) {
        verifyIsIntColumn(tableName, columnName, dataType);
    }

    @And("all the created tables SHOULD have primary key {word} {word} column")
    public void allTheCreatedTablesShouldHavePrimaryKeyBigintIdColumn(String dataType, String columnName) {
        verifyAllCreatedTableHavePK(dataType, columnName);
    }

    @And("all the created tables SHOULD have required {word}\\({int}\\) {word} column")
    public void allTheCreatedTablesShouldHaveRequiredVarcharNameColumn(String dataType, Integer length,
            String columnName) {
        verifyAllCreatedTableHaveColumnName(dataType, columnName, length);
    }

    private HikariDataSource dataSource() {
        String userName = "postgres";
        String password = "password";
        String url = "jdbc:postgresql://127.0.0.1:32543/eventstore_db";

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(userName);
        hikariConfig.setPassword(password);

        HikariDataSource cp = new HikariDataSource(hikariConfig);
        cp.setMaximumPoolSize(12);
        cp.setMinimumIdle(2);

        return cp;
    }

    private void checkSchemaExist(String schemaName) {
        String checkSchemaQuery = "SELECT schema_name FROM information_schema.schemata WHERE schema_name = '"
                + schemaName + "'";

        try (Connection con = dataSource.getConnection();
                PreparedStatement pst = con.prepareStatement(checkSchemaQuery);
                ResultSet rs = pst.executeQuery()) {

            Assertions.assertTrue(rs.next(), "Schema " + schemaName + "should exist");

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void dropSchemaIfExists(String schemaName) {
        String dropSchemaQuery = "DROP SCHEMA IF EXISTS " + schemaName + " CASCADE";

        try (Connection con = dataSource.getConnection();
                PreparedStatement pst = con.prepareStatement(dropSchemaQuery)) {
            pst.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableInSchema(String table, String schemaName) {
        String verifyTableQuery = "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = '" + schemaName + "' " +
                "AND table_name = '" + table + "'";
        try (Connection con = dataSource.getConnection();
                PreparedStatement pst = con.prepareStatement(verifyTableQuery);
                ResultSet rs = pst.executeQuery()) {

            Assertions.assertTrue(rs.next(), "Table " + table + "should exist");

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableHasColumnReferenceOtherTablePK(String table, String columnName, String referencedTable) {

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet foreignKeys = metaData.getImportedKeys(con.getCatalog(), null, table)) {
                boolean existingForeignKey = false;
                while (foreignKeys.next()) {
                    String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                    String pkTableName = foreignKeys.getString("PKTABLE_NAME");

                    if (fkColumnName.equals(columnName) && pkTableName.equals(referencedTable)) {
                        existingForeignKey = true;
                        break;
                    }
                }

                Assertions.assertTrue(existingForeignKey,
                        "Table " + table + " should have " + columnName + " column reference table " + referencedTable
                                + "'s primary key");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyIsVarcharColumn(String tableName, String columnName, String dataType, Integer length) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
                boolean found = false;
                while (columns.next()) {
                    String actualColumnDataType = columns.getString("TYPE_NAME");
                    int columnLength = columns.getInt("COLUMN_SIZE");

                    if (actualColumnDataType.equalsIgnoreCase(dataType) && columnLength == length) {
                        found = true;
                        break;
                    }
                }

                Assertions.assertTrue(found,
                        "Table " + tableName + " SHOULD have required " + dataType + "(" + length + ") " + columnName
                                + " column");

            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyIsIntColumn(String tableName, String columnName, String dataType) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
                boolean found = false;
                while (columns.next()) {
                    String actualColumnDataType = columns.getString("TYPE_NAME");

                    // Replace the alias 'int4' with 'int' to ensure consistency in type naming
                    if ("int4".equals(actualColumnDataType)) {
                        actualColumnDataType = "int";
                    }
                    System.out.println(actualColumnDataType);
                    if (actualColumnDataType.equalsIgnoreCase(dataType)) {
                        found = true;
                        break;
                    }
                }

                Assertions.assertTrue(found,
                        "Table " + tableName + " SHOULD have required " + dataType + " " + columnName + " column");

            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyAllCreatedTableHavePK(String type, String columnName) {
        Set<String> tablesMissingPK = new HashSet<>();

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet tables = metaData.getTables(con.getCatalog(), null, "%", new String[] { "TABLE" })) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    boolean pkFound = false;

                    try (ResultSet primaryKeys = metaData.getPrimaryKeys(con.getCatalog(), null, tableName)) {
                        while (primaryKeys.next()) {
                            String pkColumnName = primaryKeys.getString("COLUMN_NAME");

                            try (ResultSet columns = metaData.getColumns(con.getCatalog(), null, tableName,
                                    pkColumnName)) {
                                if (columns.next()) {
                                    String actualDataType = columns.getString("TYPE_NAME");

                                    // Replace the alias 'int8' with 'bigint' to ensure consistency in type naming
                                    if ("int8".equals(actualDataType)) {
                                        actualDataType = "bigint";
                                    }

                                    if (pkColumnName.equalsIgnoreCase(columnName)
                                            && actualDataType.equalsIgnoreCase(type)) {
                                        pkFound = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (!pkFound) {
                        tablesMissingPK.add(tableName);
                    }
                }
            }

            Assertions.assertTrue(tablesMissingPK.isEmpty(),
                    "Some tables are missing the primary key " + columnName + " of type " + type + ": "
                            + tablesMissingPK);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("SQL error: " + e.getMessage(), e);
        }

    }

    private void verifyAllCreatedTableHaveColumnName(String type, String columnName, Integer length) {
        Set<String> tablesMissingColumn = new HashSet<>();

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet tables = metaData.getTables(con.getCatalog(), null, "%", new String[] { "TABLE" })) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    boolean columnFound = false;

                    try (ResultSet columns = metaData.getColumns(con.getCatalog(), null, tableName, columnName)) {
                        while (columns.next()) {
                            String dataType = columns.getString("TYPE_NAME");
                            int columnSize = columns.getInt("COLUMN_SIZE");

                            if (dataType.equalsIgnoreCase(type) && columnSize == length) {
                                columnFound = true;
                                break;
                            }
                        }
                    }

                    if (!columnFound) {
                        tablesMissingColumn.add(tableName);
                    }
                }
            }

            Assertions.assertTrue(tablesMissingColumn.isEmpty(),
                    "Some tables are missing the column " + columnName + " of type " + type + "(" + length + "): "
                            + tablesMissingColumn);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("SQL error: " + e.getMessage(), e);
        }
    }
}
