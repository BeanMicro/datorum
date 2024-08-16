package steps.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

import org.junit.jupiter.api.Assertions;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.beandev.datorum.CreatePostgres;
import io.beandev.datorum.schema.jdbc.JdbcSchemaRepository;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;

public class DatabaseDefinitionSteps {
    private JdbcSchemaRepository jdbcSchemaRepository;
    private HikariDataSource dataSource;
    private ArrayList<String> createdTableList = new ArrayList<>();

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

    @Then("schema {schemaName} SHOULD be created")
    public void schemaShouldBeCreated(String schemaName) {
        checkSchemaExist(schemaName);
    }

    @And("table {tableName} SHOULD be created in schema {schemaName}")
    public void tableShouldBeCreatedInSchemaDatorumSchema(String tableName, String schemaName) {
        verifyTableInSchema(tableName, schemaName);

        createdTableList.add(tableName);
    }

    @And("table {tableName} SHOULD have {columnName} column reference table {referencedTableName}'s primary key")
    public void tableShouldHaveColumnReferenceTablePrimaryKey(String tableName, String columnName,
            String referencedTableName) {

        verifyTableHaveColumnReferenceToTablePrimaryKey(tableName, columnName, referencedTableName);
    }

    @And("table {tableName} SHOULD have required {dataType}\\({int}\\) {columnName} column")
    public void tableShouldHaveRequiredDataTypeColumn(String tableName, String dataType,
            Integer length, String columnName) {

        verifyTableHaveRequiredDataTypeAndNameColumn(tableName, columnName, dataType, length);

    }

    @And("table {tableName} SHOULD have {dataType} {columnName} column")
    public void tableShouldHaveDataTypeColumn(String tableName, String dataType, String columnName) {

        verifyTableHaveDataTypeAndNameColumn(tableName, columnName, dataType);
    }

    @And("all the created tables SHOULD have primary key {dataType} {columnName} column")
    public void allTheCreatedTablesShouldHavePrimaryKeyBigintIdColumn(String dataType, String columnName) {

        verifyAllCreatedTableHavePrimaryKey(createdTableList, columnName, dataType);
    }

    @And("all the created tables SHOULD have required {dataType}\\({int}\\) {columnName} column")
    public void allTheCreatedTablesShouldHaveRequiredVarcharNameColumn(String dataType, Integer length,
            String columnName) {

        verifyAllCreatedTableHaveRequiredDataTypeColumn(createdTableList, columnName, dataType, length);
    }

    @ParameterType("[a-zA-Z_][a-zA-Z0-9_]*")
    public String tableName(String table) {
        return table;
    }

    @ParameterType("[a-zA-Z_][a-zA-Z0-9_]*")
    public String referencedTableName(String referencedTable) {
        return referencedTable;
    }

    @ParameterType("[a-zA-Z_][a-zA-Z0-9_]*")
    public String schemaName(String schema) {
        return schema;
    }

    @ParameterType("[a-zA-Z_][a-zA-Z0-9_]*")
    public String columnName(String column) {
        return column;
    }

    @ParameterType("[a-zA-Z_][a-zA-Z0-9_]*")
    public String dataType(String type) {
        return type;
    }

    private HikariDataSource dataSource() {
        String userName = "postgres";
        String password = "password";
        String url = "jdbc:postgresql://127.0.0.1:5433/eventstore_db";

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

    private void verifyTableInSchema(String tableName, String schemaName) {
        String verifyTableQuery = "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = '" + schemaName + "' " +
                "AND table_name = '" + tableName + "'";
        try (Connection con = dataSource.getConnection();
                PreparedStatement pst = con.prepareStatement(verifyTableQuery);
                ResultSet rs = pst.executeQuery()) {

            Assertions.assertTrue(rs.next(), "Table " + tableName + " should exist in schema" + schemaName);

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableHaveColumnReferenceToTablePrimaryKey(String tableName, String columnName,
            String referencedTableName) {

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet foreignKeys = metaData.getImportedKeys(con.getCatalog(), null, tableName)) {
                boolean existingForeignKey = false;
                while (foreignKeys.next()) {
                    String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                    String pkTableName = foreignKeys.getString("PKTABLE_NAME");

                    if (fkColumnName.equals(columnName) && pkTableName.equals(referencedTableName)) {
                        existingForeignKey = true;
                        break;
                    }
                }

                Assertions.assertTrue(existingForeignKey,
                        "Table " + tableName + " should have " + columnName + " column reference table "
                                + referencedTableName + "'s primary key");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyTableHaveRequiredDataTypeAndNameColumn(String tableName, String columnName, String dataType,
            Integer length) {
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

    private void verifyTableHaveDataTypeAndNameColumn(String tableName, String columnName, String dataType) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
                boolean found = false;
                while (columns.next()) {
                    String actualColumnDataType = columns.getString("TYPE_NAME");

                    // Because in Postgres Integer type is named as 'int4'
                    if ("int4".equals(actualColumnDataType)) {
                        actualColumnDataType = "int";
                    }
                    if (actualColumnDataType.equalsIgnoreCase(dataType)) {
                        found = true;
                        break;
                    }
                }

                Assertions.assertTrue(found,
                        "Table " + tableName + " SHOULD have " + dataType + " " + columnName + " column");

            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyAllCreatedTableHavePrimaryKey(ArrayList<String> createdTables, String columnName,
            String dataType) {
        Set<String> tablesMissingPK = new HashSet<>();

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            for (String currentTable : createdTables) {

                boolean pkFound = false;

                try (ResultSet primaryKeys = metaData.getPrimaryKeys(con.getCatalog(), null, currentTable)) {
                    while (primaryKeys.next()) {
                        String pkColumnName = primaryKeys.getString("COLUMN_NAME");

                        try (ResultSet columns = metaData.getColumns(con.getCatalog(), null, currentTable,
                                pkColumnName)) {
                            if (columns.next()) {
                                String actualDataType = columns.getString("TYPE_NAME");

                                // Because in Postgres BigInt type is named as 'int8'
                                if ("int8".equals(actualDataType)) {
                                    actualDataType = "bigint";
                                }

                                // Condition : Primary key 'id' type is BIGINT
                                if (pkColumnName.equalsIgnoreCase(columnName)
                                        && actualDataType.equalsIgnoreCase(dataType)) {
                                    pkFound = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (!pkFound) {
                    tablesMissingPK.add(currentTable);
                }
            }

            Assertions.assertTrue(tablesMissingPK.isEmpty(),
                    "Some tables are missing the primary key '" + columnName + "' of type " + dataType + ": "
                            + tablesMissingPK);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("SQL error: " + e.getMessage(), e);
        }

    }

    private void verifyAllCreatedTableHaveRequiredDataTypeColumn(ArrayList<String> createdTables, String columnName,
            String dataType, Integer length) {
        Set<String> tablesMissingColumn = new HashSet<>();

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            for (String currentTable : createdTables) {

                boolean columnFound = false;

                try (ResultSet columns = metaData.getColumns(con.getCatalog(), null, currentTable, columnName)) {
                    while (columns.next()) {
                        String actualDataType = columns.getString("TYPE_NAME");
                        int columnSize = columns.getInt("COLUMN_SIZE");
                        // Condition : Column 'name' type is varchar(250)
                        if (actualDataType.equalsIgnoreCase(dataType) && columnSize == length) {
                            columnFound = true;
                            break;
                        }
                    }
                }

                if (!columnFound) {
                    tablesMissingColumn.add(currentTable);
                }
            }

            Assertions.assertTrue(tablesMissingColumn.isEmpty(),
                    "Some tables are missing the column '" + columnName + "' of type " + dataType + "(" + length
                            + ") : "
                            + tablesMissingColumn);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("SQL error: " + e.getMessage(), e);
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

}
