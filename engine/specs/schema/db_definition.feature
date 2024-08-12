Feature: Schema DB
  Rule: WHILE base tables are missing, THEN create them
    The schema is not empty
    Scenario: Empty database
        Given a Postgres database without schemas
        And an implementation of SchemaRepository

        When createBaseTables() is executed

        Then schema datorum_schema SHOULD be created
        And table app SHOULD be created in schema datorum_schema
        And table context SHOULD be created in schema datorum_schema
        And table context SHOULD have app_id column reference table app's primary key
        And table aggregate SHOULD be created in schema datorum_schema
        And table aggregate SHOULD have context_id column reference table context's primary key
        And table entity SHOULD be created in schema datorum_schema
        And table entity SHOULD have aggregate_id column reference table aggregate's primary key
        And table attribute SHOULD be created in schema datorum_schema
        And table attribute SHOULD have entity_id column reference table entity's primary key
        And table attribute SHOULD have required VARCHAR(25) datatype_name column
        And table attribute SHOULD have INT datatype_length column
        And table attribute SHOULD have INT datatype_scale column
        And all the created tables SHOULD have primary key BIGINT id column
        And all the created tables SHOULD have required VARCHAR(250) name column
