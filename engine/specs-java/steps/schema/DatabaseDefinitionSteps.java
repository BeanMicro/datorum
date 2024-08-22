package steps.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Map;

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
    private String currentSchema;
    private static final Map<String, String> TYPE_MAPPING = Map.of(
            "int4", "int",
            "int8", "bigint",
            "int2", "smallint",
            "bool", "boolean");

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
        currentSchema = schemaName;
    }

    @And("table {tableName} SHOULD have autoincrement primary key")
    public void tableShouldHaveAutoIncrementPrimaryKey(String tableName) {
        verifyTableShouldHasAutoIncrementPrimaryKey(tableName);
    }

    @And("table {tableName} SHOULD have {columnName} column reference table {referencedTableName}'s primary key")
    public void tableShouldHaveColumnReferenceTablePrimaryKey(String tableName, String columnName,
            String referencedTableName) {

        verifyTableHasColumnReferenceToTablePrimaryKey(tableName, columnName, referencedTableName);
    }

    @And("table {tableName} SHOULD have required {dataType}\\({int}\\) {columnName} column")
    public void tableShouldHaveRequiredDataTypeColumn(String tableName, String dataType,
            Integer length, String columnName) {

        verifyTableHasRequiredDataTypeAndNameColumn(tableName, columnName, dataType, length);

    }

    @And("table {tableName} SHOULD have {dataType} {columnName} column")
    public void tableShouldHaveDataTypeColumn(String tableName, String dataType, String columnName) {

        verifyTableHasDataTypeAndNameColumn(tableName, columnName, dataType);
    }

    @And("all the created tables SHOULD have primary key {dataType} {columnName} column")
    public void allTheCreatedTablesShouldHavePrimaryKeyBigintIdColumn(String dataType, String columnName) {

        verifyAllCreatedTableHavePrimaryKey(createdTableList, columnName, dataType);
    }

    @And("table {tableName} SHOULD have UNIQUE constraint on 2 columns {columnName} and {columnName}")
    public void tableShouldHaveUniqueConstraintColumns(String tableName, String firstColumnName,
            String secondColumnName) {
        verifyTableShouldHasUniqueConstraintOnColumns(tableName, firstColumnName, secondColumnName);
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

            Assertions.assertTrue(rs.next(), "Table " + tableName + " should exist in schema " + schemaName);

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyColumnDataType(ResultSet rs, String columnName, String expectedDataType) {
        try {
            String typeName = rs.getString("TYPE_NAME");
            String actualColumnDataType = TYPE_MAPPING.getOrDefault(typeName, typeName);
            Assertions.assertEquals(expectedDataType.toLowerCase(), actualColumnDataType,
                    "Column " + columnName + " should have datatype " + expectedDataType
                            + ". But datatype we got is " + actualColumnDataType);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyColumnName(ResultSet rs, String expectedColumnName) {
        try {
            String actualColumnName = rs.getString("COLUMN_NAME");
            Assertions.assertEquals(expectedColumnName, actualColumnName,
                    "Primary key should have column " + expectedColumnName + ". But the column we got is "
                            + actualColumnName);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyColumnLength(ResultSet rs, String columnName, Integer expectedColumnLength) {
        try {
            int actualColumnLength = rs.getInt("COLUMN_SIZE");
            Assertions.assertEquals(expectedColumnLength, actualColumnLength,
                    "Column " + columnName + " should have datatype " + expectedColumnLength
                            + ". But datatype we got is " + actualColumnLength);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyAutoIncrement(ResultSet rs, String columnName) {
        try {
            String actualAutoIncrement = rs.getString("IS_AUTOINCREMENT");

            Assertions.assertEquals("YES", actualAutoIncrement,
                    "Primary key " + columnName + " should have autoincrement");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private ResultSet verifiedPrimaryKeys(DatabaseMetaData metaData, String tableName) {
        try {
            ResultSet primaryKeys = metaData.getPrimaryKeys(null, currentSchema, tableName);
            if (!primaryKeys.next()) {
                Assertions.fail("Table " + tableName + " should have primary key.");
            }

            return primaryKeys;
        } catch (SQLException e) {
            e.printStackTrace();
            Assertions.fail("An error occurred while retrieving primary key information.");
            return null;
        }
    }

    private ResultSet verifiedColumns(DatabaseMetaData metaData, String tableName, String columnName) {
        try {
            ResultSet columns = metaData.getColumns(null, currentSchema, tableName, columnName);

            if (!columns.next()) {
                Assertions.fail("Table " + tableName + " should have primary key column " + columnName);
            }

            return columns;
        } catch (SQLException e) {
            e.printStackTrace();
            Assertions.fail("An error occurred while retrieving primary key information.");
            return null;
        }
    }

    private void verifyTableShouldHasAutoIncrementPrimaryKey(String tableName) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            ResultSet primaryKeys = verifiedPrimaryKeys(metaData, tableName);

            String columnName = primaryKeys.getString("COLUMN_NAME");

            ResultSet columns = verifiedColumns(metaData, tableName, columnName);

            verifyAutoIncrement(columns, columnName);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyTableHasColumnReferenceToTablePrimaryKey(String tableName, String columnName,
            String referencedTableName) {

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            ResultSet foreignKeys = metaData.getImportedKeys(con.getCatalog(), currentSchema, tableName);

            boolean existingForeignKey = false;
            while (foreignKeys.next()) {
                String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                String pkTableName = foreignKeys.getString("PKTABLE_NAME");

                if (!fkColumnName.equals(columnName) || !pkTableName.equals(referencedTableName)) {
                    continue;
                }

                existingForeignKey = true;
                break;
            }

            Assertions.assertTrue(existingForeignKey,
                    "There is no " + columnName + " column reference table " + referencedTableName
                            + "'s primary key");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyTableHasRequiredDataTypeAndNameColumn(String tableName, String columnName, String dataType,
            Integer length) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            ResultSet columns = verifiedColumns(metaData, tableName, columnName);

            verifyColumnDataType(columns, columnName, dataType);
            verifyColumnLength(columns, columnName, length);

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public void verifyTableShouldHasUniqueConstraintOnColumns(String tableName, String firstColumnName,
            String secondColumnName) {
        try (Connection connection = dataSource.getConnection()) {
            boolean constraintExists = false;

            boolean firstUniqueColumn = isColumnUnique(connection, currentSchema, tableName, firstColumnName);
            boolean secondUniqueColumn = isColumnUnique(connection, currentSchema, tableName, secondColumnName);

            if (!firstUniqueColumn)
                Assertions.fail("Column " + firstColumnName + " has not UNIQUE");

            if (!secondUniqueColumn)
                Assertions.fail("Column " + secondColumnName + " has not UNIQUE");

            constraintExists = true;
            Assertions.assertTrue(constraintExists);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isColumnUnique(Connection conn, String schemaName, String tableName, String columnName)
            throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getIndexInfo(null, schemaName, tableName, true, false)) {
            while (rs.next()) {
                String indexColumnName = rs.getString("COLUMN_NAME");
                if (columnName.equals(indexColumnName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void verifyTableHasDataTypeAndNameColumn(String tableName, String columnName, String dataType) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            ResultSet columns = verifiedColumns(metaData, tableName, columnName);
            verifyColumnDataType(columns, columnName, dataType);

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableHasPrimaryKey(String tableName, String columnName, String dataType) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            ResultSet primaryKeys = verifiedPrimaryKeys(metaData, tableName);
            verifyColumnName(primaryKeys, columnName);

            ResultSet columns = verifiedColumns(metaData, tableName, columnName);
            verifyColumnDataType(columns, columnName, dataType);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("SQL error: " + e.getMessage(), e);
        }
    }

    private void verifyAllCreatedTableHavePrimaryKey(ArrayList<String> createdTables, String columnName,
            String dataType) {
        for (String currentTable : createdTables) {
            verifyTableHasPrimaryKey(currentTable, columnName, dataType);
        }

    }

    private void verifyAllCreatedTableHaveRequiredDataTypeColumn(ArrayList<String> createdTables, String columnName,
            String dataType, Integer length) {
        for (String currentTable : createdTables) {
            verifyTableHasRequiredDataTypeAndNameColumn(currentTable, columnName, dataType, length);
        }
    }
}
