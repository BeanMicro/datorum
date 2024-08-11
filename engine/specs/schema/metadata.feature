Feature: Schema DB
  Rule: WHILE base tables are missing, create them
    Scenario: Empty database
        Given a Postgres database without schemas
        And an implementation of SchemaRepository

        When createBaseTables() is executed

        Then schema datorum_schema SHOULD be created
        And table app SHOULD be created in schema datorum_schema
        And table app SHOULD have primary key BIGINT autoincrement id column

        And table context SHOULD be created in schema datorum_schema
        And table context SHOULD have primary key BIGINT autoincrement id column
        And table context SHOULD have app_id column reference table app's primary key

        And table partition SHOULD be created in schema datorum_schema
        And table partition SHOULD have partition_id column

        And table table_infix SHOULD be created
        And table table_infix SHOULD have primary key BIGINT autoincrement id column
        And table table_infix SHOULD have BIGINT reference_id column
        And table table_infix SHOULD have required BIGINT partition_id column
        And table table_infix SHOULD have required VARCHAR(30) table_infix column
        And table table_infix SHOULD have UNIQUE constraint on 2 columns partition_id and table_infix

        And table aggregate SHOULD be created in schema datorum_schema
        And table aggregate SHOULD have primary key BIGINT autoincrement id column
        And table aggregate SHOULD have context_id column reference table context's primary key
        And table aggregate SHOULD have table_infix_id column reference table table_infix's primary key

        And table entity SHOULD be created in schema datorum_schema
        And table entity SHOULD have aggregate_id column reference table aggregate's primary key
        And table entity SHOULD have table_infix_id column reference table table_infix's primary key

        And table attribute SHOULD be created in schema datorum_schema
        And table attribute SHOULD have entity_id column reference table entity's primary key
        And table attribute SHOULD have required VARCHAR(25) datatype_name column
        And table attribute SHOULD have INT datatype_length column
        And table attribute SHOULD have INT datatype_scale column
        And table attribute SHOULD have table_infix_id column reference table table_infix's primary key

        And all the created tables SHOULD have primary key BIGINT id column
        And all the created tables SHOULD have required VARCHAR(250) name column


  Rule: Create schema according to the Entity's metadata

  Rule: Create partitioned snapshot tables according to the Entity's metadata
    Scenario: Single entity with VARCHAR attribute
        Given a Postgres database with schema datorum_schema
        And metadata base tables
        And app TestApp
        And context test-context in app test-app
        And aggregate dictionary in context test-context
        And partition first-database for owner dictionary-service

        When entity Word is created as aggregate root of aggregate Dictionary
        And entity Word has VARCHAR(25) attribute name
        And entity Word has table_infix ttd_word_
        And attribute name has table_infix ttd_word_name_

        Then table ttd_word_0 SHOULD be created
        And table ttd_word_0 SHOULD have primary key BIGINT autoincrement id column
        And table ttd_word_0 SHOULD have attribute

        And table ttd_word_name_0 SHOULD be created
        And table ttd_word_name_0 SHOULD have primary key BIGINT id column referencing ttd_word_0's primary key
        And table ttd_word_name_0 SHOULD have VARCHAR(25) name column

        And table event_
