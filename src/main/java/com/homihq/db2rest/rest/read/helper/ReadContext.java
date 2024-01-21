package com.homihq.db2rest.rest.read.helper;

import com.homihq.db2rest.exception.GenericDataAccessException;
import com.homihq.db2rest.exception.InvalidTableException;
import com.homihq.db2rest.mybatis.MyBatisTable;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.BasicColumn;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlCriterion;
import org.mybatis.dynamic.sql.SqlTable;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.ColumnSortSpecification;
import org.mybatis.dynamic.sql.select.QueryExpressionDSL;
import org.mybatis.dynamic.sql.select.SelectModel;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.mybatis.dynamic.sql.SqlBuilder.count;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;


@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ReadContext {

    String schemaName;
    String tableName;
    String select;
    String filter;
    Pageable pageable;
    Sort sort;

    MyBatisTable from;
    List<MyBatisTable> tables;

    boolean union;

    QueryExpressionDSL<SelectModel> queryExpressionDSL;
    SelectStatementProvider selectStatementProvider;

    public void addWhereClause(SqlCriterion condition) {
        queryExpressionDSL.where(condition);
    }

    public boolean isCountQuery() {
        return StringUtils.equals(select, "count(*)");
    }

    public ColumnSortSpecification getSortColumn(String tableName, String columnName) {
        if(StringUtils.isBlank(tableName)) {
            return
            new ColumnSortSpecification(from.getAlias(), from.column(columnName));
        }
        else {
            //find by table and column
            MyBatisTable table =
            tables.stream()
                    .filter(t -> StringUtils.equalsIgnoreCase(t.getTableName(), tableName))
                    .findFirst().orElseThrow(() -> new InvalidTableException("Sort : " + tableName));

            return
                    new ColumnSortSpecification(table.getAlias(), SqlColumn.of(columnName, SqlTable.of(tableName)));
        }

    }

    public void createSelect() {

        List<BasicColumn> columns = getAllColumns();

        detectUnion();

        if(union) {
            createUnionQuery();
        }
        else if(isCountQuery()) {
            from = getRootTable();
            queryExpressionDSL = select(count()).from(from, from.getAlias());
        }
        else{
            from = getRootTable();
            queryExpressionDSL = select(columns).from(from, from.getAlias());
        }

    }

    private MyBatisTable getRootTable() {
        return
        tables.stream().filter(MyBatisTable::isRoot).findFirst()
                .orElseThrow(() -> new GenericDataAccessException("Unable to detect root table."));
    }

    private void createUnionQuery() {

        for(MyBatisTable table : tables) {

            if(Objects.isNull(queryExpressionDSL)) {

                queryExpressionDSL = select(getAllColumns()).from(table, table.getAlias());
            }
            else {
                queryExpressionDSL.union().
                select(getAllColumns()).from(table, table.getAlias());
            }
        }


    }

    private void detectUnion() {
        Set<MyBatisTable> result = tables.stream()
                .collect(Collectors.groupingBy(Function.identity()
                        , Collectors.counting()))
                .entrySet().stream()
                .filter(m -> m.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        union = (this.tables.size() == result.size()) && result.size() > 1;

        if(result.size() > 1 && this.tables.size() > result.size())
            throw new GenericDataAccessException("Unable to create SQL. Seems union but too many tables.");
    }

    private List<BasicColumn> getAllColumns() {
        return tables.stream()
                .flatMap(t -> t.getSqlColumnList().stream())
                .map(t -> (BasicColumn)t)
                .toList();
    }

    public String prepareSQL() {

        selectStatementProvider =  queryExpressionDSL.build().render(RenderingStrategies.SPRING_NAMED_PARAMETER);

        return this.selectStatementProvider.getSelectStatement();

    }

    public Map<String,Object> prepareParameters() {
        return this.selectStatementProvider.getParameters();
    }

}
