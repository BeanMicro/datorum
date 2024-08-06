package io.beandev.datorum.schema.jdbc;

import io.beandev.datorum.schema.SchemaRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class JdbcSchemaRepository implements SchemaRepository {
    private final DataSource dataSource;

    public JdbcSchemaRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void createBaseTables() {
        try (Connection conn = dataSource.getConnection()) {
            try {
                // Disable auto commit
                conn.setAutoCommit(false);

                // Create schema
                String sql = "CREATE SCHEMA IF NOT EXISTS \"datorum_schema\"";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }

                // Check if the type already exists
                boolean exists;
                sql = "SELECT * FROM pg_catalog.pg_type JOIN pg_catalog.pg_namespace ON pg_type.typnamespace = pg_namespace.oid WHERE typname = '_apptype' AND pg_type.typtype = 'b' AND pg_namespace.nspname = 'datorum_schema'";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    ResultSet rs = pstmt.executeQuery();
                    exists = rs.next();
                }

                // If the type exists end transaction
                if (exists) {
                    conn.commit();
                    return;
                }

                // Creating types
                sql = """
                        CREATE TYPE datorum_schema.AppType AS (
                             id      BIGINT,
                             name    VARCHAR(255)
                        );
                        CREATE TYPE datorum_schema.ContextType AS (
                             id      BIGINT,
                             name    VARCHAR(255),
                             app     datorum_schema.AppType
                        );
                        CREATE TYPE datorum_schema.AggregateType AS (
                             id      BIGINT,
                             name    VARCHAR(255),
                             context datorum_schema.ContextType
                        );
                        CREATE TYPE datorum_schema.EntityType AS (
                             id          BIGINT,
                             name        VARCHAR(255),
                             aggregate   datorum_schema.AggregateType
                        );
                        CREATE TYPE datorum_schema.DataTypeEnum AS ENUM (
                             'BOOLEAN',
                             'INTEGER',
                             'LONG',
                             'FLOAT',
                             'DOUBLE',
                             'BYTES',
                             'DECIMAL',
                             'STRING',
                             'RELATION',
                             'MAP'
                        );
                        CREATE TYPE datorum_schema.DataType AS (
                             type              datorum_schema.DataTypeEnum,
                             precisionOrLength INTEGER,
                             scale             INTEGER
                        );
                        CREATE TYPE datorum_schema.AttributeType AS (
                             id                  BIGINT,
                             name                VARCHAR(255),
                             type                datorum_schema.DataType,
                             owner_entity_id     BIGINT,
                             relation_entity_id  BIGINT
                        );
                        """;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }

                // Creating tables
                sql = """
                        CREATE TABLE IF NOT EXISTS datorum_schema.system_info (
                            property_name VARCHAR(150) PRIMARY KEY,
                            property_value VARCHAR(150)
                        );
                        CREATE TABLE IF NOT EXISTS datorum_schema.app (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(255)
                        );
                        CREATE TABLE IF NOT EXISTS datorum_schema.context (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(255),
                            app_id BIGINT,
                            FOREIGN KEY (app_id) REFERENCES datorum_schema.app(id)
                        );
                        CREATE TABLE IF NOT EXISTS datorum_schema.aggregate (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(255),
                            context_id BIGINT,
                            FOREIGN KEY (context_id) REFERENCES datorum_schema.context(id)
                        );
                        CREATE TABLE IF NOT EXISTS datorum_schema.partition (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(255),
                            app_id BIGINT,
                            context_id BIGINT,
                            aggregate_id BIGINT,
                            FOREIGN KEY (context_id) REFERENCES datorum_schema.context(id)
                        );
                        CREATE TABLE IF NOT EXISTS datorum_schema.entity (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(255),
                            aggregate_id BIGINT,
                            is_root BOOLEAN,
                            FOREIGN KEY (aggregate_id) REFERENCES datorum_schema.aggregate(id)
                        );
                        CREATE TABLE IF NOT EXISTS datorum_schema.attribute (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(255),
                            type VARCHAR(50),
                            entity_id BIGINT,
                            relation_id BIGINT,
                            FOREIGN KEY (entity_id) REFERENCES datorum_schema.entity(id)
                        );
                        """;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }

                // Insert into "system_info"
                sql = "INSERT INTO \"datorum_schema\".\"system_info\" (property_name, property_value) VALUES  (?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, "schema.version");
                    pstmt.setString(2, "v1.0.0");
                    pstmt.executeUpdate();
                }

                // Commit all the changes
                conn.commit();
            } catch (SQLException e) {
                e.printStackTrace();

                // If there is exception, rollback the transaction
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            } finally {
                try {
                    // Set auto commit true at the end
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}