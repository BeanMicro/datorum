package io.beandev.datorum;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.beandev.datorum.connection.DataSourceManager;
import io.beandev.datorum.data.AttributeRecord;
import io.beandev.datorum.data.BigId;
import io.beandev.datorum.data.EntityRecord;
import io.beandev.datorum.data.Event;
import io.beandev.datorum.migration.Difference;
import io.beandev.datorum.migration.Migration;
import io.beandev.datorum.migration.Migrator;
import io.beandev.datorum.migration.Scope;
import io.beandev.datorum.migration.jdbc.JdbcMigrationRepository;
import io.beandev.datorum.repository.jooq.SimpleJooqRepository;
import io.beandev.datorum.schema.Aggregate;
import io.beandev.datorum.schema.App;
import io.beandev.datorum.schema.Attribute;
import io.beandev.datorum.schema.Context;
import io.beandev.datorum.schema.Entity;
import io.beandev.datorum.schema.jdbc.JdbcSchemaRepository;
import io.beandev.datorum.schema.jooq.JooqSchemaRepository;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.System.out;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        // TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the
        // highlighted text
        // to see how IntelliJ IDEA suggests fixing it.
        out.printf("Hello and welcome!");

        App app = new App(123, "Datorum");

        Context context = new Context((456), "Datorum Context", app);

        Aggregate aggregate = new Aggregate((789), "Datorum Aggregate", context);

        Entity root = new Entity((999), "Datorum Root", aggregate, true);

        EntityRecord rootValue = new EntityRecord(new BigId(1), root, null, "Datorum Aggregate Value");

        Entity entity = new Entity((0), "Datorum Entity", aggregate);

        EntityRecord entityValue = new EntityRecord(new BigId(1), entity, rootValue, "Datorum Entity Value");

        Attribute attribute = new Attribute(
                (1),
                "Datorum Attribute",
                new Attribute.DataType(Attribute.DataType.Type.STRING, 120),
                entity);

        AttributeRecord record = new AttributeRecord(new BigId(12), 1, attribute, entityValue, "Datorum Value");

        AttributeRecord intRecord = new AttributeRecord(new BigId(12), 1, attribute, entityValue, 333L);

        Event event = new Event(new BigId(1),
                new Event.Operation[] { new Event.Operation(Event.Operator.CREATE, new Event.Operand(record)) });

        out.println("Event: " + event);

        for (int i = 1; i <= 5; i++) {
            // TIP Press <shortcut actionId="Debug"/> to start debugging your code. We have
            // set one <icon src="AllIcons.Debugger.Db_set_breakpoint"/> breakpoint
            // for you, but you can always add more by pressing <shortcut
            // actionId="ToggleLineBreakpoint"/>.
            out.println("i = " + i);
            out.println("stringValue = " + record.value().stringValue());
            out.println("intValue = " + intRecord.value().longValue());
        }

        List<Map<String, String>> maps = new ArrayList<>(
                Arrays.asList(
                        new HashMap<>() {
                            {
                                put("key1", "value1");
                                put("key2", "value2");
                            }
                        },
                        new HashMap<>() {
                            {
                                put("key1", "value3");
                                put("key2", "value4");
                            }
                        }));

        maps.stream().peek(map -> {
            out.println(map);
            map.put("key1", "Changed or not?");
        }).forEach(out::println);

        Stream.iterate(10000, i -> i >= 9990, i -> i - 1)
                .peek(item -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .peek(item -> out.println("Before : item = " + item))
                .sorted()
                .forEach(item -> out.println("After : item = " + item));

        // The "create" reference is an instance of DSLContext
        String userName = "postgres";
        String password = "password";
        String url = "jdbc:postgresql://127.0.0.1:5432/eventstore_db";

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(userName);
        hikariConfig.setPassword(password);

        HikariDataSource cp = new HikariDataSource(hikariConfig);
        cp.setMaximumPoolSize(12);
        cp.setMinimumIdle(2);

        // try (HikariDataSource ds = new HikariDataSource(config)) {
        // ds.setMaximumPoolSize(12);
        // ds.setMinimumIdle(2);

        // Connection is the only JDBC resource that we need
        // PreparedStatement and ResultSet are handled by jOOQ, internally
        try (Connection conn = cp.getConnection()) {
            DSLContext create = DSL.using(conn, SQLDialect.POSTGRES);
            // DSLContext create = DSL.using(ds, SQLDialect.POSTGRES);

            // Fetch a SQL string from a jOOQ Query in order to manually execute it with
            // another tool.
            // For simplicity reasons, we're using the API to construct case-insensitive
            // object references, here.
            Query query = create.select(field("book.title")
            // , field("AUTHOR.FIRST_NAME"), field("AUTHOR.LAST_NAME")
            )
                    .from(table("book"))
            // .join(table("AUTHOR"))
            // .on(field("BOOK.AUTHOR_ID").eq(field("AUTHOR.ID")))
            // .where(field("BOOK.PUBLISHED_IN").eq(1948))
            ;

            String sql = query.getSQL();
            List<Object> bindValues = query.getBindValues();

            var queryStmt = create.select(field("book.title"))
                    .from(table("book"));
            Result<?> result = queryStmt.fetch();
            result.stream().forEach(out::println);

            queryStmt.fetch().stream().forEach(title -> out.println("Title: " + title.value1()));

        }
        // For the sake of this tutorial, let's keep exception handling simple
        catch (Exception e) {
            e.printStackTrace();
        }

        ObjectMapper mapper = new ObjectMapper();

        try {
            String jsonString = mapper.writeValueAsString(record);
            System.out.println(jsonString);
        } catch (Exception e) {
            e.printStackTrace();
        }

        var dsm = new DataSourceManager(
                new HashMap<String, DataSource>() {
                    {
                        put("primary", cp);
                    }
                });

        dsm.putDataSource("secondary", cp);

        SimpleJooqRepository repo = new SimpleJooqRepository<Aggregate, BigId>(
                Aggregate.class,
                cp);
        repo.findAll();

        // JooqSchemaRepository schemaRepository = new JooqSchemaRepository(cp);
        // schemaRepository.createBaseTables();

        JdbcSchemaRepository jdbcSchemaRepository = new JdbcSchemaRepository(cp);
        jdbcSchemaRepository.createBaseTables();

        Migration schemaMigration = new Migration(
                1,
                20240715122600L,
                new Difference[] {
                        new Difference(
                                0,
                                "name",
                                Scope.AGGREGATE)
                });

        JdbcMigrationRepository migrationRepository = new JdbcMigrationRepository(cp);
        migrationRepository.createBaseTables();
        Migrator m = new Migrator(migrationRepository);
        m.apply(schemaMigration);

        // Migration schemaMigration = new Migration(
        // new Command[]{
        // new Command(new EntityCommand(entity), Command.Action.CREATE)
        // }
        // );
        // JooqMigrationRepository migrationRepository = new
        // JooqMigrationRepository(cp);
        // migrationRepository.createBaseTables();
        //
        // out.println(schemaMigration.hash());
        // Migrator m = new Migrator(migrationRepository);
        // m.progress();
    }
}
