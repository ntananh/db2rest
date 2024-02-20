package com.homihq.db2rest.schema;

import com.homihq.db2rest.dialect.Dialect;
import com.homihq.db2rest.exception.GenericDataAccessException;
import com.homihq.db2rest.exception.InvalidTableException;
import com.homihq.db2rest.mybatis.MyBatisTable;
import com.homihq.db2rest.model.DbTable;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import schemacrawler.schema.*;
import schemacrawler.schemacrawler.*;
import schemacrawler.tools.utility.SchemaCrawlerUtility;
import us.fatehi.utility.datasource.DatabaseConnectionSources;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.homihq.db2rest.schema.NameUtil.getAlias;

@Slf4j
@RequiredArgsConstructor
@Component
public final class SchemaManager {

    private final DataSource dataSource;
    private final Map<String, Table> tableMap = new ConcurrentHashMap<>();
    private final List<Table> tableList = new ArrayList<>();

    private final AliasGenerator aliasGenerator;

    private final List<Dialect> dialects;

    @Getter
    private Dialect dialect;

    @PostConstruct
    public void reload() {
        createSchemaCache();
    }

    public void createSchemaCache() {

        // Create the options
        final LimitOptionsBuilder limitOptionsBuilder = LimitOptionsBuilder.builder().tableTypes("TABLE","VIEW","MATERIALIZED VIEW");

        final LoadOptionsBuilder loadOptionsBuilder = LoadOptionsBuilder.builder()
                // Set what details are required in the schema - this affects the
                // time taken to crawl the schema
                .withSchemaInfoLevel(SchemaInfoLevelBuilder.standard());

        final SchemaCrawlerOptions options = SchemaCrawlerOptionsBuilder.newSchemaCrawlerOptions()
                .withLimitOptions(limitOptionsBuilder.toOptions()).withLoadOptions(loadOptionsBuilder.toOptions());

        // Get the schema definition

        final Catalog catalog = SchemaCrawlerUtility.getCatalog(DatabaseConnectionSources.fromDataSource(dataSource), options);

        DatabaseInfo databaseInfo = catalog.getDatabaseInfo();

        log.info("Database - {}", databaseInfo);

        for(Dialect dialect : dialects) {

            if(dialect.canSupport(databaseInfo)) {
                this.dialect = dialect;
                break;
            }
        }

        for (final Schema schema : catalog.getSchemas()) {

            for (final Table table : catalog.getTables(schema)) {
                log.debug("{}", table.getFullName());
                String schemaName = getSchemaName(table);

                String fullName = schemaName + "." + table.getName();

                tableMap.put(fullName, table);
                tableList.add(table);

            }
        }
    }

    private String getSchemaName(Table table) {
        //TODO - move DB specific handling to Dialect class
        String schemaName = table.getSchema().getCatalogName();

        if(StringUtils.isBlank(schemaName)) {
            //POSTGRESQL

            schemaName = table.getSchema().getName();
        }
        log.debug("schemaName -> {}", schemaName);
        return schemaName;
    }

    @Deprecated
    public MyBatisTable getTable(String tableName) {
        List<MyBatisTable> tables = findTables(tableName);

        if(tables.size() != 1) throw new GenericDataAccessException("Unable to find table with name - " + tableName);

        return tables.get(0);
    }

    public DbTable getTableV2(String tableName) {
        List<DbTable> tables = findTablesV2(tableName);

        if(tables.size() != 1) throw new GenericDataAccessException("Unable to find table with name - " + tableName);

        return tables.get(0);
    }

    public List<DbTable> findTablesV2(String tableName) {
        return tableList.stream()
                .filter(t -> StringUtils.equalsIgnoreCase(t.getName(), tableName))
                .toList()
                .stream()
                .map(t ->
                        new DbTable(
                                getSchemaName(t), tableName,
                                aliasGenerator.getAlias("", 4, tableName), t))
                .toList();

    }

    public Optional<Table> getTable(String schemaName, String tableName) {
        Table table = tableMap.get(schemaName + "." + tableName);
        return Optional.of(table);
    }

    @Deprecated
    public MyBatisTable getOneTable(String schemaName, String tableName) {
        Table table = getTable(schemaName, tableName).orElseThrow(() -> new InvalidTableException(tableName));
        return new MyBatisTable(schemaName, tableName, table);

    }


    public DbTable getOneTableV2(String schemaName, String tableName) {
        Table table = getTable(schemaName, tableName).orElseThrow(() -> new InvalidTableException(tableName));

        return new DbTable(
                getSchemaName(table), tableName,
                aliasGenerator.getAlias("", 4, tableName), table);


    }

    public List<ForeignKey> getForeignKeysBetween(MyBatisTable rootTable, MyBatisTable childTable) {
        //1. first locate table in the cache
        Table table = getTable(rootTable.getSchemaName(), rootTable.getTableName())
                .orElseThrow(() -> new InvalidTableException(rootTable.getTableName()));

        log.debug("Table - {}", table);
        log.debug("Table - {}", table.getImportedForeignKeys());

        //2. if foreign keys = null, look for join table option
        return table.getImportedForeignKeys().stream()
                .filter(fk -> StringUtils.equalsIgnoreCase(
                        getSchemaName(fk.getParent()), rootTable.getSchemaName())
                        && StringUtils.equalsIgnoreCase(fk.getReferencedTable().getName(), childTable.getTableName())).toList();
    }

    @Deprecated
    public List<ForeignKey> getForeignKeysBetween(String schemaName, String rootTable, String childTable) {

      //1. first locate table in the cache
      Table table = getTable(schemaName, rootTable).orElseThrow(() -> new InvalidTableException(rootTable));

      //2. if foreign keys = null, look for join table option
      return table.getImportedForeignKeys().stream()
              .filter(fk -> StringUtils.equalsIgnoreCase(fk.getSchema().getCatalogName(), schemaName)
                      && StringUtils.equalsIgnoreCase(fk.getReferencedTable().getName(), childTable)).toList();
    }

    public List<MyBatisTable> findTables(String tableName) {
        return tableList.stream()
                .filter(t -> StringUtils.equalsIgnoreCase(t.getName(), tableName))
                .toList()
                .stream()
                .map(t ->
                        new MyBatisTable(
                                getSchemaName(t), tableName, t))
                .toList();

    }

    public MyBatisTable findTable(String schemaName, String tableName, int counter) {

        Table table = getTable(schemaName, tableName).orElseThrow(() -> new InvalidTableException(tableName));

        MyBatisTable myBatisTable = new MyBatisTable(schemaName, tableName, table);
        myBatisTable.setAlias(getAlias(counter, ""));

        return myBatisTable;
    }
}
