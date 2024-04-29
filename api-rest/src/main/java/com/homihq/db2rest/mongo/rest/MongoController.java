package com.homihq.db2rest.mongo.rest;


import com.homihq.db2rest.config.Db2RestConfigProperties;
import com.homihq.db2rest.core.dto.CreateResponse;
import com.homihq.db2rest.core.dto.DeleteResponse;
import com.homihq.db2rest.mongo.repository.MongoRepository;
import com.homihq.db2rest.mongo.rest.api.MongoRestApi;
import com.homihq.db2rest.mongo.rsql.RsqlMongodbAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@Slf4j
@RequiredArgsConstructor
public class MongoController implements MongoRestApi {

    private final MongoRepository mongoRepository;

    private final RsqlMongodbAdapter rsqlMongodbAdapter;

    private final Db2RestConfigProperties db2RestConfigProperties;

    @Override
    public CreateResponse save(String collectionName,
                               List<String> includeFields,
                               Map<String, Object> data) {
        return mongoRepository
                .save(collectionName, includeFields, data);

    }

    @Override
    public DeleteResponse delete(String collectionName, String filter) {
        log.debug("Filter - {}", filter);
        db2RestConfigProperties.checkDeleteAllowed(filter);
        var query = new Query();
        addCriteria(filter, query);
        return mongoRepository.delete(query, collectionName);
    }

    @Override
    public Object findAll(String collectionName, String fields, String filter, List<String> sorts,
                          int limit, long offset) {
        fields = StringUtils.trim(fields);
        log.debug("Filter - {}", filter);
        log.debug("Fields - {}", fields);
        log.info("limit - {}", limit);
        log.info("offset - {}", offset);

        var query = new Query();

        if (limit > -1) {
            query.limit(limit);
        }

        if (offset > -1) {
            query.skip(offset);
        }

        addCriteria(filter, query);
        includeFields(fields, query);
        sortDirection(sorts).ifPresent(fieldSort -> sortFields(fieldSort, query));

        return mongoRepository.find(query, collectionName);
    }

    private static Optional<FieldSort> sortDirection(List<String> sorts) {
        return sorts.stream()
                .map(sort -> sort.split(";"))
                .filter(sortParts -> sortParts.length == 2)
                .map(sortParts -> new FieldSort(sortParts[0], sortParts[1].equalsIgnoreCase("DESC") ?
                        Sort.Direction.DESC : Sort.Direction.ASC))
                .findFirst();
    }

    private record FieldSort(String field, Sort.Direction sortDirection) {
    }

    @Override
    public Map<String, Object> findOne(String collectionName, String fields, String filter) {
        var query = new Query();
        addCriteria(filter, query);
        includeFields(fields, query);
        return mongoRepository.findOne(query, collectionName);
    }

    private void addCriteria(String filter, Query query) {
        if (StringUtils.isNotBlank(filter)) {
            query.addCriteria(rsqlMongodbAdapter.getCriteria(filter, Object.class));
        }
    }

    private void includeFields(String fields, Query query) {
        if (!StringUtils.equals("*", fields)) {
            query.fields().include(fields.split(","));
        }
    }

    private void sortFields(FieldSort fieldSort, Query query) {
        query.with(Sort.by(fieldSort.sortDirection, fieldSort.field));
    }
}