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

                // Create schemas
                String sql = """
                        CREATE SCHEMA IF NOT EXISTS "datorum_schema";
                        CREATE SCHEMA IF NOT EXISTS "datorum_events";
                        CREATE SCHEMA IF NOT EXISTS "datorum_snapshots";
                        CREATE SCHEMA IF NOT EXISTS "datorum_views";
                        """;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }

                // Creating tables
                sql = """
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."app" (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(250)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."context" (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(250),
                            app_id BIGINT,
                            FOREIGN KEY (app_id) REFERENCES "datorum_schema"."app"(id)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."aggregate" (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(250),
                            context_id BIGINT,
                            FOREIGN KEY (context_id) REFERENCES "datorum_schema"."context"(id)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."entity" (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(250),
                            aggregate_id BIGINT UNIQUE,
                            table_infix_id BIGINT,
                            is_root BOOLEAN UNIQUE,
                            FOREIGN KEY (aggregate_id) REFERENCES "datorum_schema"."aggregate"(id),
                            FOREIGN KEY (table_infix_id) REFERENCES "datorum_schema"."table_infix"(id)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."attribute" (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(250),
                            datatype_name VARCHAR(25),
                            datatype_length INT,
                            datatype_scale INT,
                            entity_id BIGINT,
                            table_infix_id BIGINT,
                            FOREIGN KEY (entity_id) REFERENCES "datorum_schema"."entity"(id),
                            FOREIGN KEY (table_infix_id) REFERENCES "datorum_schema"."table_infix"(id)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."cluster" (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(250),
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."datasource" (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(250),
                            cluster_id BIGINT,
                            FOREIGN KEY (cluster_id) REFERENCES "datorum_schema"."cluster"(id)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."shard" (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(250),
                            datasource_id BIGINT,
                            FOREIGN KEY (datasource_id) REFERENCES "datorum_schema"."datasource"(id)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."sharding" (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(250),
                            is_query BOOLEAN,
                            shard_id BIGINT,
                            FOREIGN KEY (shard_id) REFERENCES "datorum_schema"."shard"(id)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."sharding_aggregate" (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(250),
                            size SMALLINT,
                            sharding_id BIGINT,
                            aggregate_id BIGINT,
                            FOREIGN KEY (sharding_id) REFERENCES "datorum_schema"."sharding"(id),
                            FOREIGN KEY (aggregate_id) REFERENCES "datorum_schema"."aggregate"(id)
                        );
                        CREATE TABLE IF NOT EXISTS "datorum_schema"."table_infix" (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(250),
                            reference_id BIGINT,
                            table_infix BIGIT,
                            table_infix VARCHAR(30)
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
