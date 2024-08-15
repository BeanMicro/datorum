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

    @And("table app SHOULD be created in schema datorum_schema")
    public void tableAppShouldBeCreatedInSchemaDatorumSchema() {
        String tableName = "app";
        String schemaName = "datorum_schema";
        verifyTableAppInSchema(tableName, schemaName);
    }

    @And("table context SHOULD be created in schema datorum_schema")
    public void tableContextShouldBeCreatedInSchemaDatorumSchema() {
        String tableName = "context";
        String schemaName = "datorum_schema";
        verifyTableContextInSchema(tableName, schemaName);
    }

    @And("table context SHOULD have app_id column reference table app's primary key")
    public void tableContextShouldHaveAppIdColumnReferenceTableAppPrimaryKey() {
        String tableName = "context";
        String columnName = "app_id";
        String referenceTableName = "app";
        verifyTableContextHaveColumnReferenceToPrimaryKey(tableName, columnName, referenceTableName);
    }

    @And("table aggregate SHOULD be created in schema datorum_schema")
    public void tableAggregateShouldBeCreatedInSchemaDatorumSchema() {
        String tableName = "aggregate";
        String schemaName = "datorum_schema";
        verifyTableAggregateInSchema(tableName, schemaName);
    }

    @And("table aggregate SHOULD have context_id column reference table context's primary key")
    public void tableAggregateShouldHaveContextIdColumnReferenceTableContextPrimaryKey() {
        String tableName = "aggregate";
        String columnName = "context_id";
        String referenceTableName = "context";
        verifyTableAggregateHaveColumnReferenceToPrimaryKey(tableName, columnName, referenceTableName);
    }

    @And("table entity SHOULD be created in schema datorum_schema")
    public void tableEntityShouldBeCreatedInSchemaDatorumSchema() {
        String tableName = "entity";
        String schemaName = "datorum_schema";
        verifyTableEntityInSchema(tableName, schemaName);
    }

    @And("table entity SHOULD have aggregate_id column reference table aggregate's primary key")
    public void tableEntityShouldHaveAggregateIdColumnReferenceTableAggregatePrimaryKey() {
        String tableName = "entity";
        String columnName = "aggregate_id";
        String referenceTableName = "aggregate";
        verifyTableEntityHaveColumnReferenceToPrimaryKey(tableName, columnName, referenceTableName);
    }

    @And("table attribute SHOULD be created in schema datorum_schema")
    public void tableAttributeShouldBeCreatedInSchemaDatorumSchema() {
        String tableName = "attribute";
        String schemaName = "datorum_schema";
        verifyTableAttributeInSchema(tableName, schemaName);
    }

    @And("table attribute SHOULD have entity_id column reference table entity's primary key")
    public void tableAttributeShouldHaveEntityIdColumnReferenceTableEntityPrimaryKey() {
        String tableName = "attribute";
        String columnName = "entity_id";
        String referenceTableName = "entity";
        verifyTableAttributeHaveColumnReferenceToPrimaryKey(tableName, columnName, referenceTableName);
    }

    @And("table attribute SHOULD have required VARCHAR\\(25\\) datatype_name column")
    public void tableAttributeShouldHaveRequiredVarcharDatatypeNameColumn() {
        String tableName = "attribute";
        String columnName = "datatype_name";
        String dataType = "VARCHAR";
        Integer length = 25;
        verifyTableAttributeHaveDataTypeNameColumn(tableName, columnName, dataType, length);
    }

    @And("table attribute SHOULD have INT datatype_length column")
    public void tableAttributeShouldHaveIntDatatypeLengthColumn() {
        String tableName = "attribute";
        String columnName = "datatype_length";
        String dataType = "INT";
        verifyTableAttributeHaveDataTypeLengthColumn(tableName, columnName, dataType);
    }

    @And("table attribute SHOULD have INT datatype_scale column")
    public void tableAttributeShouldHaveIntDatatypeScaleColumn() {
        String tableName = "attribute";
        String columnName = "datatype_scale";
        String dataType = "INT";
        verifyTableAttributeHaveDataTypeScaleColumn(tableName, columnName, dataType);
    }

    @And("all the created tables SHOULD have primary key BIGINT id column")
    public void allTheCreatedTablesShouldHavePrimaryKeyBigintIdColumn() {
        String columnName = "id";
        String dataType = "BIGINT";
        verifyAllCreatedTableHavePrimaryKey(columnName, dataType);
    }

    @And("all the created tables SHOULD have required VARCHAR\\(250\\) name column")
    public void allTheCreatedTablesShouldHaveRequiredVarcharNameColumn() {
        String columnName = "name";
        String dataType = "VARCHAR";
        Integer length = 250;
        verifyAllCreatedTableHaveNameColumn(columnName, dataType, length);
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

    private void verifyTableAppInSchema(String tableName, String schemaName) {
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

    private void verifyTableContextInSchema(String tableName, String schemaName) {
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

    private void verifyTableEntityInSchema(String tableName, String schemaName) {
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

    private void verifyTableAggregateInSchema(String tableName, String schemaName) {
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

    private void verifyTableAttributeInSchema(String tableName, String schemaName) {
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

    private void verifyTableContextHaveColumnReferenceToPrimaryKey(String tableName, String columnName,
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

    private void verifyTableEntityHaveColumnReferenceToPrimaryKey(String tableName, String columnName,
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

    private void verifyTableAggregateHaveColumnReferenceToPrimaryKey(String tableName, String columnName,
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

    private void verifyTableAttributeHaveColumnReferenceToPrimaryKey(String tableName, String columnName,
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

    private void verifyTableAttributeHaveDataTypeNameColumn(String tableName, String columnName, String dataType,
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

    private void verifyTableAttributeHaveDataTypeLengthColumn(String tableName, String columnName, String dataType) {
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
                    System.out.println(actualColumnDataType);
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

    private void verifyTableAttributeHaveDataTypeScaleColumn(String tableName, String columnName, String dataType) {
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
                    System.out.println(actualColumnDataType);
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

    private void verifyAllCreatedTableHavePrimaryKey(String columnName, String dataType) {
        Set<String> tablesMissingPK = new HashSet<>();

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet tables = metaData.getTables(con.getCatalog(), null, "%", new String[] { "TABLE" })) {
                while (tables.next()) {
                    String currentTable = tables.getString("TABLE_NAME");
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
            }

            Assertions.assertTrue(tablesMissingPK.isEmpty(),
                    "Some tables are missing the primary key '" + columnName + "' of type " + dataType + ": "
                            + tablesMissingPK);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("SQL error: " + e.getMessage(), e);
        }

    }

    private void verifyAllCreatedTableHaveNameColumn(String columnName, String dataType, Integer length) {
        Set<String> tablesMissingColumn = new HashSet<>();

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet tables = metaData.getTables(con.getCatalog(), null, "%", new String[] { "TABLE" })) {
                while (tables.next()) {
                    String currentTable = tables.getString("TABLE_NAME");
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
            }

            Assertions.assertTrue(tablesMissingColumn.isEmpty(),
                    "Some tables are missing the column '" + columnName + "' of type " + dataType + "(" + length
                            + ") : "
                            + tablesMissingColumn);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("SQL error: " + e.getMessage(), e);
        }
    }
}
