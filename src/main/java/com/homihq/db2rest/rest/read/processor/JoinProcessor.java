package com.homihq.db2rest.rest.read.processor;

import com.homihq.db2rest.rest.read.dto.JoinDetail;
import com.homihq.db2rest.rest.read.dto.ReadContextV2;
import com.homihq.db2rest.rest.read.model.DbColumn;
import com.homihq.db2rest.rest.read.model.DbJoin;
import com.homihq.db2rest.rest.read.model.DbTable;
import com.homihq.db2rest.rest.read.model.DbWhere;
import com.homihq.db2rest.rest.read.processor.rsql.operator.handler.OperatorMap;
import com.homihq.db2rest.rest.read.processor.rsql.parser.RSQLParserBuilder;
import com.homihq.db2rest.rest.read.processor.rsql.visitor.BaseRSQLVisitor;
import com.homihq.db2rest.schema.SchemaManager;
import cz.jirutka.rsql.parser.ast.Node;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import schemacrawler.schema.Column;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.homihq.db2rest.schema.TypeMapperUtil.getJdbcType;


@Component
@Slf4j
@Order(6)
@RequiredArgsConstructor
public class JoinProcessor implements ReadProcessor {

    private final SchemaManager schemaManager;
    private final OperatorMap operatorMap;
    @Override
    public void process(ReadContextV2 readContextV2) {
        List<JoinDetail> joins = readContextV2.getJoins();

        if(Objects.isNull(joins) || joins.isEmpty()) return;

        DbTable rootTable = readContextV2.getRoot();

        for(JoinDetail joinDetail : joins) {
            String tableName = joinDetail.table();

            DbTable table = schemaManager.getTableV2(tableName);

            List<DbColumn> columnList = addColumns(table, joinDetail.fields());

            readContextV2.addColumns(columnList);

            addJoin(table, rootTable, joinDetail, readContextV2);
        }
    }

    private void addJoin(DbTable table, DbTable rootTable, JoinDetail joinDetail, ReadContextV2 readContextV2) {
        DbJoin join = new DbJoin();
        join.setTableName(table.name());
        join.setAlias(table.alias());
        join.setJoinType(joinDetail.getJoinType());

        addCondition(table, rootTable, joinDetail, join);

        processFilter(table, joinDetail, join, readContextV2);

        readContextV2.addJoin(join);

    }

    private void processFilter(DbTable table, JoinDetail joinDetail, DbJoin join,
                               ReadContextV2 readContextV2) {
        if(joinDetail.hasFilter()){
            readContextV2.createParamMap();

            DbWhere dbWhere = new DbWhere(
                    table.name(),
                    table,table.buildColumns(),readContextV2.getParamMap());


            Node rootNode = RSQLParserBuilder.newRSQLParser().parse(joinDetail.filter());

            String where = rootNode
                    .accept(new BaseRSQLVisitor(
                            dbWhere));

            join.addAdditionalWhere(where);


        }

    }

    private void addCondition(DbTable table, DbTable rootTable, JoinDetail joinDetail, DbJoin dbJoin) {

        if(joinDetail.hasOn()) {
            int onIdx = 1;
            for(String on : joinDetail.on()) {
                processOn(on, onIdx, table, rootTable, dbJoin);
                onIdx++;
            }
        }

    }

    private void processOn(String onExpression, int onIdx, DbTable table, DbTable rootTable, DbJoin dbJoin) {
        String rSqlOperator = this.operatorMap.getRSQLOperator(onExpression);
        String operator = this.operatorMap.getSQLOperator(rSqlOperator);

        String left = onExpression.substring(0, onExpression.indexOf(rSqlOperator)).trim();
        String right = onExpression.substring(onExpression.indexOf(rSqlOperator) + rSqlOperator.length()).trim();


        DbColumn leftColumn = rootTable.buildColumn(left);
        DbColumn rightColumn = table.buildColumn(right);

        if(onIdx == 1) {
            dbJoin.addOn(leftColumn, operator, rightColumn);
        }
        else{
            dbJoin.addAndCondition(leftColumn, operator, rightColumn);
        }

    }


    private DbColumn createColumn(String columnName, DbTable table) {
        Column column = table.lookupColumn(columnName);

        return new DbColumn(table.name(), columnName, getJdbcType(column) , column, "", table.alias());
    }

    private List<DbColumn> addColumns(DbTable table, List<String> fields) {

        //There are 2 possibilities
        // - field can be *
        // - can be set of fields from the given table

        log.info("Fields - {}", fields);

        List<DbColumn> columnList = new ArrayList<>();

        if(Objects.isNull(fields)) {//include all fields of root table
            columnList.addAll( table.buildColumns());
        }
        else{ //query has specific columns so parse and map it.
            List<DbColumn> columns =
                    fields.stream()
                            .map(table::buildColumn)
                            .toList();
            columnList.addAll(columns);
        }

        return columnList;
    }


}
