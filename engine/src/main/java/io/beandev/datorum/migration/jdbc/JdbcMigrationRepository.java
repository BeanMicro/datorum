package io.beandev.datorum.migration.jdbc;

import io.beandev.datorum.migration.AggregateSnapshot;
import io.beandev.datorum.migration.Difference;
import io.beandev.datorum.migration.Migration;
import io.beandev.datorum.migration.MigrationRepository;
import io.beandev.datorum.schema.Aggregate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;

public class JdbcMigrationRepository implements MigrationRepository {

    private final DataSource dataSource;

    public JdbcMigrationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Migration save(Migration migration) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            // save migration to migration table
            String insertMigrationSql = "INSERT INTO datorum_schema.migration (parent_id, id, previous_parent_id, previous_id, hash, status) VALUES (?, ?, ?, ?, ?, ?::datorum_schema.statusenum)";
            try (PreparedStatement stmt = conn.prepareStatement(insertMigrationSql)) {
                stmt.setLong(1, migration.parentId());
                stmt.setLong(2, migration.id());
                var previousMigrationParentId = migration.previousMigrationParentId();
                if (previousMigrationParentId != null) {
                    stmt.setLong(3, previousMigrationParentId);
                } else {
                    stmt.setNull(3, java.sql.Types.BIGINT);
                }
                var previousMigrationId = migration.previousMigrationId();
                if (previousMigrationId != null) {
                    stmt.setLong(4, previousMigrationId);
                } else {
                    stmt.setNull(4, java.sql.Types.BIGINT);
                }

                stmt.setString(5, migration.hash());
                stmt.setString(6, migration.status() != null ? migration.status().name() : null);
                stmt.executeUpdate();
            }

            // save differences in difference table
            String insertDifferenceSql = "INSERT INTO datorum_schema.difference (id, migration_parent_id, migration_id, name, scope, action, data_type, relation_entity_id, owner_entity_id) VALUES (?, ?, ?, ?, ?::datorum_schema.scopeenum, ?::datorum_schema.differenceactionenum, ?::datorum_schema.datatype, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertDifferenceSql)) {
                for (Difference difference : migration.differences()) {
                    stmt.setLong(1, difference.id());
                    stmt.setLong(2, migration.parentId());
                    stmt.setLong(3, migration.id());
                    stmt.setString(4, difference.name());
                    stmt.setString(5, difference.scope().name());
                    stmt.setString(6, difference.action().name());
                    stmt.setString(7, difference.dataTypeName());
                    var relationEntityId = difference.relationEntityId();
                    if (relationEntityId != null) {
                        stmt.setLong(8, relationEntityId);
                    } else {
                        stmt.setNull(8, java.sql.Types.BIGINT);
                    }
                    var ownerEntityId = difference.ownerEntityId();
                    if (ownerEntityId != null) {
                        stmt.setLong(9, ownerEntityId);
                    } else {
                        stmt.setNull(9, java.sql.Types.BIGINT);
                    }
                    stmt.executeUpdate();
                }
            }

            conn.commit();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return migration;
    }

    @Override
    public void createBaseTables() {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Check the types
                ResultSet rs = stmt.executeQuery("""
                                SELECT *
                                FROM pg_catalog.pg_type
                                         JOIN pg_catalog.pg_namespace
                                              ON pg_type.typnamespace = pg_namespace.oid
                                WHERE typname = '_scopeenum'
                                  AND pg_namespace.nspname = 'datorum_schema'
                                  AND pg_type.typtype = 'b'
                        """);

                if (rs.next()) {
                    return;
                }
                // Execute TYPE creation
                stmt.execute("""
                            CREATE TYPE datorum_schema.ScopeEnum AS ENUM(
                                'APP',
                                'CONTEXT',
                                'AGGREGATE',
                                'ENTITY',
                                'ATTRIBUTE'
                            );

                            CREATE TYPE datorum_schema.DifferenceActionEnum AS ENUM (
                                'CREATE',
                                'RENAME',
                                'CHANGE_ATTRIBUTE_OWNER',
                                'CHANGE_DATA_TYPE',
                                'INCREASE',
                                'DECREASE',
                                'DELETE'
                            );

                            CREATE TYPE datorum_schema.DifferenceType AS (
                                id        BIGINT,
                                name      VARCHAR(255),
                                scope     datorum_schema.ScopeEnum,
                                action    datorum_schema.DifferenceActionEnum,
                                data_type datorum_schema.DataType,
                                relation  datorum_schema.EntityType,
                                owner     datorum_schema.EntityType
                            );

                            CREATE TYPE datorum_schema.StatusEnum AS ENUM (
                                'IN_PROGRESS',
                                'COMPLETED',
                                'FAILED',
                                'PENDING'
                            );
                        """);

                // Execute table creation and data insertion
                stmt.execute(
                        """
                                        CREATE TABLE IF NOT EXISTS datorum_schema.migration (
                                        parent_id              BIGINT,
                                        id                     BIGINT,
                                        previous_parent_id     BIGINT,
                                        previous_id            BIGINT,
                                        hash                   VARCHAR(40),
                                        status                 datorum_schema.StatusEnum,
                                        PRIMARY KEY (parent_id, id),
                                        FOREIGN KEY (previous_parent_id, previous_id) REFERENCES datorum_schema.migration(parent_id, id)
                                    );

                                    CREATE TABLE IF NOT EXISTS datorum_schema.difference (
                                        id                     BIGINT PRIMARY KEY,
                                        migration_parent_id    BIGINT,
                                        migration_id           BIGINT,
                                        name                VARCHAR(255),
                                        scope               datorum_schema.ScopeEnum,
                                        action              datorum_schema.DifferenceActionEnum,
                                        data_type           datorum_schema.DataType,
                                        relation_entity_id  BIGINT,
                                        owner_entity_id     BIGINT,
                                        FOREIGN KEY (migration_parent_id, migration_id) REFERENCES datorum_schema.migration(parent_id, id)
                                    );

                                    CREATE INDEX difference__migration_id ON datorum_schema.difference (migration_parent_id, migration_id);

                                    INSERT INTO datorum_schema.system_info (property_name, property_value)
                                    VALUES ('migration.version', 'v1.0.0');
                                """);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException(e);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Stream<Migration> forEachPastMigrations(Migration latestMigration) {
        // code goes here
        return null;
    }

    @Override
    public AggregateSnapshot findAggregateSnapshot(Aggregate aggregate) {
        // code goes here
        return null;
    }

    @Override
    public Stream<Migration> findByAggregate(Aggregate aggregate) {
        // code goes here
        return null;
    }
}