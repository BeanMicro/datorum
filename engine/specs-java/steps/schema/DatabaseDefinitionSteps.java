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
        dropSchemaIfItExists(schemaName);
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
        verifyTableExistsInSchema(tableName, schemaName);

        createdTableList.add(tableName);
        currentSchema = schemaName;
    }

    @And("table {tableName} SHOULD have autoincrement primary key")
    public void tableShouldHaveAutoIncrementPrimaryKey(String tableName) {
        verifyTableHasAutoIncrementPrimaryKey(tableName);
    }

    @And("table {tableName} SHOULD have {columnName} column reference table {referencedTableName}'s primary key")
    public void tableShouldHaveColumnReferenceTablePrimaryKey(String tableName, String columnName,
            String referencedTableName) {

        verifyTableHasColumnReferenceToTablePrimaryKey(tableName, columnName, referencedTableName);
    }

    @And("table {tableName} SHOULD have required {dataType}\\({int}\\) {columnName} column")
    public void tableShouldHaveRequiredDataTypeAndLengthColumn(String tableName, String dataType,
            Integer length, String columnName) {

        verifyTableHasColumnWithDatatypeAndLength(tableName, columnName, dataType, length);

    }

    @And("table {tableName} SHOULD have {dataType} {columnName} column")
    public void tableShouldHaveDataTypeColumn(String tableName, String dataType, String columnName) {

        verifyTableHasColumnWithDatatype(tableName, columnName, dataType);
    }

    @And("all the created tables SHOULD have primary key {dataType} {columnName} column")
    public void allTheCreatedTablesShouldHavePrimaryKeyDataTypeColumn(String dataType, String columnName) {

        verifyAllCreatedTablesHavePrimaryKeyWithDataType(createdTableList, columnName, dataType);
    }

    @And("table {tableName} SHOULD have UNIQUE constraint on 2 columns {columnName} and {columnName}")
    public void tableShouldHaveUniqueConstraintColumns(String tableName, String firstColumnName,
            String secondColumnName) {
        verifyTableHasUniqueColumns(tableName, firstColumnName, secondColumnName);
    }

    @And("all the created tables SHOULD have required {dataType}\\({int}\\) {columnName} column")
    public void allTheCreatedTablesShouldHaveRequiredDataTypeAndLengthColumn(String dataType, Integer length,
            String columnName) {
        verifyAllCreatedTablesHaveRequiredDataTypeAndLengthColumn(createdTableList, columnName, dataType, length);
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

    private void dropSchemaIfItExists(String schemaName) {
        String dropSchemaQuery = "DROP SCHEMA IF EXISTS " + schemaName + " CASCADE";

        try (Connection con = dataSource.getConnection();
                PreparedStatement pst = con.prepareStatement(dropSchemaQuery)) {
            pst.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableExistsInSchema(String tableName, String schemaName) {
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

    private void verifyColumnDataType(ResultSet columnResultSet, String expectedDataType) {
        try {
            String typeName = columnResultSet.getString("TYPE_NAME");
            String actualColumnDataType = TYPE_MAPPING.getOrDefault(typeName, typeName);
            Assertions.assertEquals(expectedDataType.toLowerCase(), actualColumnDataType,
                    "Column should have datatype " + expectedDataType
                            + ". But datatype we got is " + actualColumnDataType);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyPrimaryKeyColumn(ResultSet primaryKeyResultSet, String expectedColumnName) {
        try {
            String actualColumnName = primaryKeyResultSet.getString("COLUMN_NAME");
            Assertions.assertEquals(expectedColumnName, actualColumnName,
                    "Primary key should have column " + expectedColumnName + ". But the column we got is "
                            + actualColumnName);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyColumnLength(ResultSet columnResultSet, Integer expectedColumnLength) {
        try {
            int actualColumnLength = columnResultSet.getInt("COLUMN_SIZE");

            Assertions.assertEquals(expectedColumnLength, actualColumnLength,
                    "Column should have datatype length" + expectedColumnLength
                            + ". But datatype length we got is " + actualColumnLength);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyColumnIsAutoIncrement(ResultSet primaryKeyResultSet) {
        try {
            String actualAutoIncrement = primaryKeyResultSet.getString("IS_AUTOINCREMENT");

            Assertions.assertEquals("YES", actualAutoIncrement,
                    "Primary key should have autoincrement");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private ResultSet retrievePrimaryKeyDescriptions(DatabaseMetaData metaData, String tableName) {
        try {
            ResultSet primaryKeyResultSet = metaData.getPrimaryKeys(null, currentSchema, tableName);
            Assertions.assertTrue(primaryKeyResultSet.next(),
                    "Table " + tableName + " should have primary key.");

            return primaryKeyResultSet;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ResultSet retrieveColumnDescriptions(DatabaseMetaData metaData, String tableName, String columnName) {
        try {
            ResultSet columnResultSet = metaData.getColumns(null, currentSchema, tableName, columnName);
            Assertions.assertTrue(columnResultSet.next(),
                    "Table " + tableName + " should have primary key column " + columnName);
            return columnResultSet;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void verifyTableHasAutoIncrementPrimaryKey(String tableName) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            ResultSet primaryKeyResultSet = retrievePrimaryKeyDescriptions(metaData, tableName);

            String columnName = primaryKeyResultSet.getString("COLUMN_NAME");

            ResultSet columnResultSet = retrieveColumnDescriptions(metaData, tableName, columnName);

            verifyColumnIsAutoIncrement(columnResultSet);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyTableHasColumnReferenceToTablePrimaryKey(String tableName, String columnName,
            String referencedTableName) {

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            ResultSet foreignKeyResultSet = metaData.getImportedKeys(con.getCatalog(), currentSchema, tableName);

            boolean existingForeignKey = false;
            while (foreignKeyResultSet.next()) {
                String fkColumnName = foreignKeyResultSet.getString("FKCOLUMN_NAME");
                String pkTableName = foreignKeyResultSet.getString("PKTABLE_NAME");

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

    private void verifyTableHasColumnWithDatatypeAndLength(String tableName, String columnName, String dataType,
            Integer length) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            ResultSet columnResultSet = retrieveColumnDescriptions(metaData, tableName, columnName);

            verifyColumnDataType(columnResultSet, dataType);
            verifyColumnLength(columnResultSet, length);

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public void verifyTableHasUniqueColumns(String tableName, String firstColumnName,
            String secondColumnName) {
        try (Connection connection = dataSource.getConnection()) {

            boolean firstUniqueColumn = isColumnUnique(connection, currentSchema, tableName, firstColumnName);
            boolean secondUniqueColumn = isColumnUnique(connection, currentSchema, tableName, secondColumnName);

            Assertions.assertTrue(firstUniqueColumn, "There is no UNIQUE column " + firstColumnName);
            Assertions.assertTrue(secondUniqueColumn, "There is no UNIQUE column " + secondColumnName);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isColumnUnique(Connection conn, String schemaName, String tableName, String columnName)
            throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet uniqueColumnResultSet = metaData.getIndexInfo(null, schemaName, tableName, true, false)) {
            while (uniqueColumnResultSet.next()) {
                String indexColumnName = uniqueColumnResultSet.getString("COLUMN_NAME");
                if (columnName.equals(indexColumnName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void verifyTableHasColumnWithDatatype(String tableName, String columnName, String dataType) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            ResultSet columnResultSet = retrieveColumnDescriptions(metaData, tableName, columnName);
            verifyColumnDataType(columnResultSet, dataType);

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableHasPrimaryKeyColumnWithDataType(String tableName, String columnName, String dataType) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            ResultSet primaryKeyResultSet = retrievePrimaryKeyDescriptions(metaData, tableName);
            verifyPrimaryKeyColumn(primaryKeyResultSet, columnName);

            ResultSet columnResultSet = retrieveColumnDescriptions(metaData, tableName, columnName);
            verifyColumnDataType(columnResultSet, dataType);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyAllCreatedTablesHavePrimaryKeyWithDataType(ArrayList<String> createdTables, String columnName,
            String dataType) {
        for (String currentTable : createdTables) {
            verifyTableHasPrimaryKeyColumnWithDataType(currentTable, columnName, dataType);
        }

    }

    private void verifyAllCreatedTablesHaveRequiredDataTypeAndLengthColumn(ArrayList<String> createdTables,
            String columnName, String dataType, Integer length) {
        for (String currentTable : createdTables) {
            verifyTableHasColumnWithDatatypeAndLength(currentTable, columnName, dataType, length);
        }
    }
}
