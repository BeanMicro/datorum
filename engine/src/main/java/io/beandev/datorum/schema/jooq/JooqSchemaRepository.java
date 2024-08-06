package io.beandev.datorum.schema.jooq;

import io.beandev.datorum.schema.SchemaRepository;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class JooqSchemaRepository implements SchemaRepository {
    private final DataSource dataSource;

    public JooqSchemaRepository(DataSource ds) {
        dataSource = ds;
    }

    @Override
    public void createBaseTables() {
        try (var conn = dataSource.getConnection()) {
            // Initialize the DSLContext
            DSLContext create = DSL.using(conn, SQLDialect.POSTGRES);

            // Start the transaction
            create.transaction(configuration -> {
                DSLContext ctx = DSL.using(configuration);
                ctx.execute("""
                            CREATE SCHEMA IF NOT EXISTS "datorum_schema"
                        """);

                Result<Record> result = ctx.select()
                        .from(table("pg_catalog.pg_type"))
                        .join(table("pg_catalog.pg_namespace"))
                        .on(field("pg_type.typnamespace").eq(field("pg_namespace.oid")))
                        .where(field("typname").eq("_apptype"))
                        .and(field("pg_namespace.nspname").eq("datorum_schema"))
                        .and(field("pg_type.typtype").eq("b"))
                        .fetch();

                if (result.stream().count() > 0) {
                    return;
                }

                ctx.execute("""
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
                        """);

                ctx.execute("""
                            CREATE TABLE IF NOT EXISTS datorum_schema.system_info (
                                property_name VARCHAR(150) PRIMARY KEY,
                                property_value VARCHAR(150)
                            );
                            INSERT INTO datorum_schema.system_info (property_name, property_value)
                            VALUES ('schema.version', 'v1.0.0');
                        """);
                ctx.execute("""
                            CREATE TABLE IF NOT EXISTS datorum_schema.app (
                                id BIGINT PRIMARY KEY,
                                name VARCHAR(255)
                            )
                        """);
                ctx.execute("""
                            CREATE TABLE IF NOT EXISTS datorum_schema.context (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(255),
                            app_id BIGINT,
                            FOREIGN KEY (app_id) REFERENCES datorum_schema.app(id)
                            )
                        """);
                ctx.execute("""
                            CREATE TABLE IF NOT EXISTS datorum_schema.aggregate (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(255),
                            context_id BIGINT,
                            FOREIGN KEY (context_id) REFERENCES datorum_schema.context(id)
                            )
                        """);
                ctx.execute("""
                            CREATE TABLE IF NOT EXISTS datorum_schema.partition (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(255),
                            app_id BIGINT,
                            context_id BIGINT,
                            aggregate_id BIGINT,
                            FOREIGN KEY (context_id) REFERENCES datorum_schema.context(id)
                            )
                        """);
                ctx.execute("""
                            CREATE TABLE IF NOT EXISTS datorum_schema.entity (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(255),
                            aggregate_id BIGINT,
                            is_root BOOLEAN,
                            FOREIGN KEY (aggregate_id) REFERENCES datorum_schema.aggregate(id)
                            )
                        """);
                ctx.execute("""
                            CREATE TABLE IF NOT EXISTS datorum_schema.attribute (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(255),
                            type VARCHAR(50),
                            entity_id BIGINT,
                            relation_id BIGINT,
                            FOREIGN KEY (entity_id) REFERENCES datorum_schema.entity(id)
                            )
                        """);
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
