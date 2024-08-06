package io.beandev.datorum;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.suite.api.AfterSuite;
import org.junit.platform.suite.api.BeforeSuite;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import io.beandev.datorum.migration.jdbc.JdbcMigrationRepositoryTest;

@Suite
@Tag("MyTestSuite")
@SelectPackages({ "io.beandev.datorum.schema.jdbc", "io.beandev.datorum.migration.jdbc" })
// @ExtendWith(SuiteTestImpl.class)
public class TestSuite {

    @BeforeSuite
    static void beforeAll() throws Exception {
        System.out.println("Setting up the database...");
        CreateDatabase.getInstance();
    }

    @AfterSuite
    static void afterAll() throws Exception {
        System.out.println("Dropping down the database...");
        CreateDatabase.removeDatabase();
    }

}
