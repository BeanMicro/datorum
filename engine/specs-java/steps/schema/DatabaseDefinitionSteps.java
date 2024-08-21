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
    private String currentSchema;

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
        verifyTableShouldHaveAutoIncrementPrimaryKey(tableName);
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

    @And("table {tableName} SHOULD have UNIQUE constraint on 2 columns {columnName} and {columnName}")
    public void tableShouldHaveUniqueConstraintColumns(String tableName, String firstColumnName,
            String secondColumnName) {
        verifyTableShouldHaveUniqueConstraintOnColumns(tableName, firstColumnName, secondColumnName);
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

    private void verifyTableShouldHaveAutoIncrementPrimaryKey(String tableName) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            ResultSet primaryKeys = metaData.getPrimaryKeys(null, currentSchema, tableName);
            boolean isAutoIncrement = false;

            if (!primaryKeys.next()) {
                Assertions.fail("Table " + tableName + " should have primary key.");
            }

            String columnName = primaryKeys.getString("COLUMN_NAME");
            try (ResultSet columns = metaData.getColumns(null, currentSchema, tableName, columnName)) {
                if (!columns.next()) {
                    Assertions.fail("Table " + tableName + " should have primary key column " + columnName);
                }

                String isAutoIncrementStr = columns.getString("IS_AUTOINCREMENT");

                if ("NO".equals(isAutoIncrementStr)) {
                    Assertions.fail("Primary key " + columnName + " should have autoincrement ");
                }

                isAutoIncrement = true;
                Assertions.assertTrue(isAutoIncrement);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyTableHaveColumnReferenceToTablePrimaryKey(String tableName, String columnName,
            String referencedTableName) {

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet foreignKeys = metaData.getImportedKeys(con.getCatalog(), currentSchema, tableName)) {
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
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyTableHaveRequiredDataTypeAndNameColumn(String tableName, String columnName, String dataType,
            Integer length) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet columns = metaData.getColumns(null, currentSchema, tableName, columnName)) {
                boolean found = false;
                if (!columns.next()) {
                    Assertions.fail("Table " + tableName + " should have column " + columnName);
                }

                String actualColumnDataType = columns.getString("TYPE_NAME");
                int columnLength = columns.getInt("COLUMN_SIZE");

                if (!actualColumnDataType.equalsIgnoreCase(dataType)) {
                    Assertions.fail("Column " + columnName + " should have datatype " + dataType
                            + ". But actual datatype we got is " + actualColumnDataType);
                }

                if (columnLength != length) {
                    Assertions.fail("Datatype " + dataType + " length should be " + length
                            + ". But actual length we got is " + columnLength);
                }

                found = true;
                Assertions.assertTrue(found);

            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public void verifyTableShouldHaveUniqueConstraintOnColumns(String tableName, String firstColumnName,
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

    private void verifyTableHaveDataTypeAndNameColumn(String tableName, String columnName, String dataType) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet columns = metaData.getColumns(null, currentSchema, tableName, columnName)) {
                boolean found = false;
                if (!columns.next()) {
                    Assertions.fail("Table " + tableName + "should have column " + columnName);
                }

                String actualColumnDataType = columns.getString("TYPE_NAME");

                // Normalize the data type names
                switch (actualColumnDataType) {
                    case "int4":
                        actualColumnDataType = "int";
                        break;
                    case "int8":
                        actualColumnDataType = "bigint";
                        break;
                    case "int2":
                        actualColumnDataType = "smallint";
                        break;
                    case "bool":
                        actualColumnDataType = "boolean";
                        break;
                    default:
                        break;
                }

                if (!actualColumnDataType.equalsIgnoreCase(dataType)) {
                    Assertions.fail("Column " + columnName + " should have datatype " + dataType
                            + ".But datatype we got is " + actualColumnDataType);
                }

                found = true;
                Assertions.assertTrue(found);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyAllCreatedTableHavePrimaryKey(ArrayList<String> createdTables, String columnName,
            String dataType) {

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();
            boolean allPKFound = false;

            for (String currentTable : createdTables) {

                try (ResultSet primaryKeys = metaData.getPrimaryKeys(con.getCatalog(), currentSchema, currentTable)) {
                    if (!primaryKeys.next()) {
                        Assertions.fail("Table " + currentTable + " should have primary key");
                    }

                    String pkColumnName = primaryKeys.getString("COLUMN_NAME");

                    if (!pkColumnName.equalsIgnoreCase(columnName)) {
                        Assertions.fail("Primary Key should have column" + columnName
                                + ". But the column we got is " + pkColumnName);
                    }

                    try (ResultSet columns = metaData.getColumns(con.getCatalog(), currentSchema, currentTable,
                            pkColumnName)) {
                        if (!columns.next()) {
                            Assertions.fail("Table " + currentTable
                                    + " should have primary key column " + pkColumnName);
                        }

                        String actualDataType = columns.getString("TYPE_NAME");

                        // Because in Postgres BigInt type is named as 'int8'
                        if ("int8".equals(actualDataType)) {
                            actualDataType = "bigint";
                        }

                        if (!actualDataType.equalsIgnoreCase(dataType)) {
                            Assertions.fail("Primary Key should have datatype" + dataType
                                    + ". But the datatype we got is " + actualDataType);
                        }

                    }
                }
            }
            allPKFound = true;
            Assertions.assertTrue(allPKFound);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("SQL error: " + e.getMessage(), e);
        }

    }

    private void verifyAllCreatedTableHaveRequiredDataTypeColumn(ArrayList<String> createdTables, String columnName,
            String dataType, Integer length) {

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();
            boolean allColumnFound = false;

            for (String currentTable : createdTables) {

                try (ResultSet columns = metaData.getColumns(con.getCatalog(), currentSchema, currentTable,
                        columnName)) {

                    if (!columns.next()) {
                        Assertions.fail("Table " + currentTable + " should have column " + columnName);
                    }

                    String actualDataType = columns.getString("TYPE_NAME");

                    int columnSize = columns.getInt("COLUMN_SIZE");

                    if (!actualDataType.equalsIgnoreCase(dataType)) {
                        Assertions.fail("Column " + columnName + " should have datatype " + dataType
                                + ". But the datatype we got is " + actualDataType);
                    }

                    if (columnSize != length) {
                        Assertions.fail("Datatype " + dataType + " length should be " + length
                                + ". But the length we got is " + columnSize);
                    }

                }
            }

            allColumnFound = true;
            Assertions.assertTrue(allColumnFound);

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
