package com.homihq.db2rest.rest.read.processor.pre;

import com.homihq.db2rest.rest.read.dto.ReadContextV2;
import com.homihq.db2rest.rsql.operators.SimpleRSQLOperators;
import com.homihq.db2rest.rsql.parser.JoinWhereFilterVisitor;
import com.homihq.db2rest.rsql.parser.WhereFilterVisitor;
import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.Node;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlCriterion;
import org.mybatis.dynamic.sql.select.join.JoinCriterion;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Order(6)
public class RootWhereProcessor implements ReadPreProcessor {
    @Override
    public void process(ReadContextV2 readContextV2) {
        if(StringUtils.isNotBlank(readContextV2.getFilter())) {

            log.info("-Creating root where condition -");

            Node rootNode = new RSQLParser(SimpleRSQLOperators.customOperators()).parse(readContextV2.getFilter());

            JoinCriterion condition = rootNode
                    .accept(new JoinWhereFilterVisitor(readContextV2.getRootTable()));

            log.info("condition - {}", condition);
            //readContextV2.addWhereCondition(condition);

        }
    }
}
