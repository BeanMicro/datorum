package io.beandev.datorum.schema.jdbc;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import io.beandev.datorum.schema.SchemaRepository;

public class JdbcSchemaRepository implements SchemaRepository {
    private final DataSource dataSource;

    public JdbcSchemaRepository(DataSource ds) {
        dataSource = ds;
    }

    @Override
    public void createBaseTables() {
        try (Connection conn = dataSource.getConnection()) {
            try {

                // Create schema
                String sql = "CREATE SCHEMA IF NOT EXISTS \"datorum_schema\"";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }

                // Creating tables
                sql = """
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."app" (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(250)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."context" (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(250),
                            app_id BIGINT,
                            FOREIGN KEY (app_id) REFERENCES "datorum_schema"."app"(id)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."aggregate" (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(250),
                            context_id BIGINT,
                            FOREIGN KEY (context_id) REFERENCES "datorum_schema"."context"(id)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."entity" (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(250),
                            aggregate_id BIGINT,
                            is_root BOOLEAN,
                            FOREIGN KEY (aggregate_id) REFERENCES "datorum_schema"."aggregate"(id)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."attribute" (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(250),
                            datatype_name VARCHAR(25),
                            datatype_length INT,
                            datatype_scale INT,
                            entity_id BIGINT,
                            FOREIGN KEY (entity_id) REFERENCES "datorum_schema"."entity"(id)
                        );
                        """;

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
