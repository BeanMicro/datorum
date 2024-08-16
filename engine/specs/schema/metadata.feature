Feature: Schema DB
  Rule: WHILE base tables are missing, THEN create them
    The schema is not empty
    Scenario: Empty database
        Given a Postgres database without schemas
        And an implementation of SchemaRepository

        When createBaseTables() is executed

        Then schema datorum_schema SHOULD be created
        And schema datorum_events SHOULD be created
        And schema datorum_snapshots SHOULD be created
        And schema datorum_views SHOULD be created

        And table cluster SHOULD be created in schema datorum_schema
        And table cluster SHOULD have autoincrement primary key
        And table datasource SHOULD be created in schema datorum_schema
        And table datasource SHOULD have autoincrement primary key
        And table datasource SHOULD have cluster_id column reference table cluster's primary key
        And table shard SHOULD be created in schema datorum_schema
        And table shard SHOULD have autoincrement primary key
        And table shard SHOULD have datasource_id column reference table datasource's primary key

        And table sharding SHOULD be created in schema datorum_schema
        And table sharding SHOULD have autoincrement primary key
        And table sharding SHOULD have BOOLEAN is_query column
        And table sharding SHOULD have shard_id column reference table shard's primary key

        And table sharding_aggregate SHOULD be created in schema datorum_schema
        And table sharding_aggregate SHOULD have id column reference table sharding's primary key
        And table sharding_aggregate SHOULD have aggregate_id column reference table aggregate's primary key
        And table sharding_aggregate SHOULD have SMALLINT size column

        And table table_infix SHOULD be created
        And table table_infix SHOULD have autoincrement primary key
        And table table_infix SHOULD have BIGINT reference_id column
        And table table_infix SHOULD have required VARCHAR(30) table_infix column

        And table app SHOULD be created in schema datorum_schema
        And table app SHOULD have autoincrement primary key

        And table context SHOULD be created in schema datorum_schema
        And table context SHOULD have autoincrement primary key
        And table context SHOULD have app_id column reference table app's primary key

        And table aggregate SHOULD be created in schema datorum_schema
        And table aggregate SHOULD have autoincrement primary key
        And table aggregate SHOULD have context_id column reference table context's primary key

        And table entity SHOULD be created in schema datorum_schema
        And table entity SHOULD have autoincrement primary key
        And table entity SHOULD have aggregate_id column reference table aggregate's primary key
        And table entity SHOULD have table_infix_id column reference table table_infix's primary key
        And table entity SHOULD have BOOLEAN is_root column
        And table entity SHOULD have UNIQUE constraint on 2 columns aggregate_id and is_root

        And table attribute SHOULD be created in schema datorum_schema
        And table attribute SHOULD have autoincrement primary key
        And table attribute SHOULD have entity_id column reference table entity's primary key
        And table attribute SHOULD have required VARCHAR(25) datatype_name column
        And table attribute SHOULD have INT datatype_length column
        And table attribute SHOULD have INT datatype_scale column
        And table attribute SHOULD have table_infix_id column reference table table_infix's primary key

        And all the created tables SHOULD have primary key BIGINT id column
        And all the created tables SHOULD have required VARCHAR(250) name column
