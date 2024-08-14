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

        verifyTableAppInSchema();
    }

    @And("table context SHOULD be created in schema datorum_schema")
    public void tableContextShouldBeCreatedInSchemaDatorumSchema() {

        verifyTableContextInSchema();
    }

    @And("table context SHOULD have app_id column reference table app's primary key")
    public void tableContextShouldHaveAppIdColumnReferenceTableAppPrimaryKey() {

        verifyTableContextHaveColumnReferenceToPrimaryKey();
    }

    @And("table aggregate SHOULD be created in schema datorum_schema")
    public void tableAggregateShouldBeCreatedInSchemaDatorumSchema() {

        verifyTableAggregateInSchema();
    }

    @And("table aggregate SHOULD have context_id column reference table context's primary key")
    public void tableAggregateShouldHaveContextIdColumnReferenceTableContextPrimaryKey() {

        verifyTableAggregateHaveColumnReferenceToPrimaryKey();
    }

    @And("table entity SHOULD be created in schema datorum_schema")
    public void tableEntityShouldBeCreatedInSchemaDatorumSchema() {

        verifyTableEntityInSchema();
    }

    @And("table entity SHOULD have aggregate_id column reference table aggregate's primary key")
    public void tableEntityShouldHaveAggregateIdColumnReferenceTableAggregatePrimaryKey() {

        verifyTableEntityHaveColumnReferenceToPrimaryKey();
    }

    @And("table attribute SHOULD be created in schema datorum_schema")
    public void tableAttributeShouldBeCreatedInSchemaDatorumSchema() {

        verifyTableAttributeInSchema();
    }

    @And("table attribute SHOULD have entity_id column reference table entity's primary key")
    public void tableAttributeShouldHaveEntityIdColumnReferenceTableEntityPrimaryKey() {

        verifyTableAttributeHaveColumnReferenceToPrimaryKey();
    }

    @And("table attribute SHOULD have required VARCHAR\\(25\\) datatype_name column")
    public void tableAttributeShouldHaveRequiredVarcharDatatypeNameColumn() {

        verifyTableAttributeHaveDataTypeNameColumn();
    }

    @And("table attribute SHOULD have INT datatype_length column")
    public void tableAttributeShouldHaveIntDatatypeLengthColumn() {

        verifyTableAttributeHaveDataTypeLengthColumn();
    }

    @And("table attribute SHOULD have INT datatype_scale column")
    public void tableAttributeShouldHaveIntDatatypeScaleColumn() {

        verifyTableAttributeHaveDataTypeScaleColumn();
    }

    @And("all the created tables SHOULD have primary key BIGINT id column")
    public void allTheCreatedTablesShouldHavePrimaryKeyBigintIdColumn() {

        verifyAllCreatedTableHavePrimaryKey();
    }

    @And("all the created tables SHOULD have required VARCHAR\\(250\\) name column")
    public void allTheCreatedTablesShouldHaveRequiredVarcharNameColumn() {

        verifyAllCreatedTableHaveNameColumn();
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

    private void verifyTableAppInSchema() {
        String verifyTableQuery = "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'datorum_schema' " +
                "AND table_name = 'app'";
        try (Connection con = dataSource.getConnection();
                PreparedStatement pst = con.prepareStatement(verifyTableQuery);
                ResultSet rs = pst.executeQuery()) {

            Assertions.assertTrue(rs.next(), "Table app should exist");

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableContextInSchema() {
        String verifyTableQuery = "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'datorum_schema' " +
                "AND table_name = 'context'";
        try (Connection con = dataSource.getConnection();
                PreparedStatement pst = con.prepareStatement(verifyTableQuery);
                ResultSet rs = pst.executeQuery()) {

            Assertions.assertTrue(rs.next(), "Table context should exist");

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableEntityInSchema() {
        String verifyTableQuery = "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'datorum_schema' " +
                "AND table_name = 'entity'";
        try (Connection con = dataSource.getConnection();
                PreparedStatement pst = con.prepareStatement(verifyTableQuery);
                ResultSet rs = pst.executeQuery()) {

            Assertions.assertTrue(rs.next(), "Table entity should exist");

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableAggregateInSchema() {
        String verifyTableQuery = "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'datorum_schema' " +
                "AND table_name = 'aggregate'";
        try (Connection con = dataSource.getConnection();
                PreparedStatement pst = con.prepareStatement(verifyTableQuery);
                ResultSet rs = pst.executeQuery()) {

            Assertions.assertTrue(rs.next(), "Table aggregate should exist");

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableAttributeInSchema() {
        String verifyTableQuery = "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'datorum_schema' " +
                "AND table_name = 'attribute'";
        try (Connection con = dataSource.getConnection();
                PreparedStatement pst = con.prepareStatement(verifyTableQuery);
                ResultSet rs = pst.executeQuery()) {

            Assertions.assertTrue(rs.next(), "Table attribute should exist");

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableContextHaveColumnReferenceToPrimaryKey() {

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet foreignKeys = metaData.getImportedKeys(con.getCatalog(), null, "context")) {
                boolean existingForeignKey = false;
                while (foreignKeys.next()) {
                    String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                    String pkTableName = foreignKeys.getString("PKTABLE_NAME");

                    if (fkColumnName.equals("app_id") && pkTableName.equals("app")) {
                        existingForeignKey = true;
                        break;
                    }
                }

                Assertions.assertTrue(existingForeignKey,
                        "Table context should have app_id column reference table app's primary key");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyTableEntityHaveColumnReferenceToPrimaryKey() {

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet foreignKeys = metaData.getImportedKeys(con.getCatalog(), null, "entity")) {
                boolean existingForeignKey = false;
                while (foreignKeys.next()) {
                    String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                    String pkTableName = foreignKeys.getString("PKTABLE_NAME");

                    if (fkColumnName.equals("aggregate_id") && pkTableName.equals("aggregate")) {
                        existingForeignKey = true;
                        break;
                    }
                }

                Assertions.assertTrue(existingForeignKey,
                        "Table entity should have aggregate_id column reference table aggregate's primary key");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyTableAggregateHaveColumnReferenceToPrimaryKey() {

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet foreignKeys = metaData.getImportedKeys(con.getCatalog(), null, "aggregate")) {
                boolean existingForeignKey = false;
                while (foreignKeys.next()) {
                    String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                    String pkTableName = foreignKeys.getString("PKTABLE_NAME");

                    if (fkColumnName.equals("context_id") && pkTableName.equals("context")) {
                        existingForeignKey = true;
                        break;
                    }
                }

                Assertions.assertTrue(existingForeignKey,
                        "Table aggregate should have context_id column reference table context's primary key");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyTableAttributeHaveColumnReferenceToPrimaryKey() {

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet foreignKeys = metaData.getImportedKeys(con.getCatalog(), null, "attribute")) {
                boolean existingForeignKey = false;
                while (foreignKeys.next()) {
                    String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                    String pkTableName = foreignKeys.getString("PKTABLE_NAME");

                    if (fkColumnName.equals("entity_id") && pkTableName.equals("entity")) {
                        existingForeignKey = true;
                        break;
                    }
                }

                Assertions.assertTrue(existingForeignKey,
                        "Table attribute should have entity_id column reference table entity's primary key");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void verifyTableAttributeHaveDataTypeNameColumn() {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet columns = metaData.getColumns(null, null, "attribute", "datatype_name")) {
                boolean found = false;
                while (columns.next()) {
                    String actualColumnDataType = columns.getString("TYPE_NAME");
                    int columnLength = columns.getInt("COLUMN_SIZE");

                    if (actualColumnDataType.equalsIgnoreCase("varchar") && columnLength == 25) {
                        found = true;
                        break;
                    }
                }

                Assertions.assertTrue(found,
                        "Table attribue SHOULD have required varchar(25) datatype_name column");

            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableAttributeHaveDataTypeLengthColumn() {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet columns = metaData.getColumns(null, null, "attribute", "datatype_length")) {
                boolean found = false;
                while (columns.next()) {
                    String actualColumnDataType = columns.getString("TYPE_NAME");

                    // Because in Postgres Integer type is named as 'int4'
                    if ("int4".equals(actualColumnDataType)) {
                        actualColumnDataType = "int";
                    }
                    System.out.println(actualColumnDataType);
                    if (actualColumnDataType.equalsIgnoreCase("INT")) {
                        found = true;
                        break;
                    }
                }

                Assertions.assertTrue(found,
                        "Table attribute SHOULD have INT datatype_length column");

            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyTableAttributeHaveDataTypeScaleColumn() {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet columns = metaData.getColumns(null, null, "attribute", "datatype_scale")) {
                boolean found = false;
                while (columns.next()) {
                    String actualColumnDataType = columns.getString("TYPE_NAME");

                    // Because in Postgres Integer type is named as 'int4'
                    if ("int4".equals(actualColumnDataType)) {
                        actualColumnDataType = "int";
                    }
                    System.out.println(actualColumnDataType);
                    if (actualColumnDataType.equalsIgnoreCase("INT")) {
                        found = true;
                        break;
                    }
                }

                Assertions.assertTrue(found,
                        "Table attribute SHOULD have INT datatype_scale column");

            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void verifyAllCreatedTableHavePrimaryKey() {
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

                                    // Because in Postgres BigInt type is named as 'int8'
                                    if ("int8".equals(actualDataType)) {
                                        actualDataType = "bigint";
                                    }

                                    // Condition : Primary key 'id' type is BIGINT
                                    if (pkColumnName.equalsIgnoreCase("id")
                                            && actualDataType.equalsIgnoreCase("BIGINT")) {
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
                    "Some tables are missing the primary key 'id' of type BIGINT: "
                            + tablesMissingPK);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("SQL error: " + e.getMessage(), e);
        }

    }

    private void verifyAllCreatedTableHaveNameColumn() {
        Set<String> tablesMissingColumn = new HashSet<>();

        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();

            try (ResultSet tables = metaData.getTables(con.getCatalog(), null, "%", new String[] { "TABLE" })) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    boolean columnFound = false;

                    try (ResultSet columns = metaData.getColumns(con.getCatalog(), null, tableName, "name")) {
                        while (columns.next()) {
                            String dataType = columns.getString("TYPE_NAME");
                            int columnSize = columns.getInt("COLUMN_SIZE");
                            // Condition : Column 'name' type is varchar(250)
                            if (dataType.equalsIgnoreCase("varchar") && columnSize == 250) {
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
                    "Some tables are missing the column 'name' of type varchar(250) : "
                            + tablesMissingColumn);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("SQL error: " + e.getMessage(), e);
        }
    }
}
