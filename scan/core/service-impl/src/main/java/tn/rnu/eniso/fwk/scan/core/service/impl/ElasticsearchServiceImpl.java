package tn.rnu.eniso.fwk.scan.core.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.rnu.eniso.fwk.scan.core.infra.model.Alert;
import tn.rnu.eniso.fwk.scan.core.service.api.ElasticsearchService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ElasticsearchServiceImpl implements ElasticsearchService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchServiceImpl.class);

    @Value("${elasticsearch.host:localhost}")
    private String elasticsearchHost;

    @Value("${elasticsearch.port:9200}")
    private int elasticsearchPort;

    @Value("${elasticsearch.index.alerts:suricata-alerts}")
    private String alertsIndex;

    private RestClient restClient;
    private ElasticsearchClient esClient;

    @PostConstruct
    public void init() {
        try {
            restClient = RestClient.builder(
                    new HttpHost(elasticsearchHost, elasticsearchPort, "http")).build();

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());

            RestClientTransport transport = new RestClientTransport(
                    restClient,
                    new JacksonJsonpMapper(objectMapper));

            esClient = new ElasticsearchClient(transport);

            createIndexIfNotExists();
            log.info("Elasticsearch client initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Elasticsearch client", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (restClient != null) {
                restClient.close();
            }
        } catch (Exception e) {
            log.error("Error closing Elasticsearch client", e);
        }
    }

    @Override
    public String indexAlert(Alert alert) {
        try {
            IndexResponse response = esClient.index(i -> i
                    .index(alertsIndex)
                    .document(alert));

            log.debug("Indexed alert with ID: {}", response.id());
            return response.id();
        } catch (Exception e) {
            log.error("Error indexing alert", e);
            return null;
        }
    }

    @Override
    public List<Alert> searchAlerts(String queryString, int from, int size) {
        try {
            SearchResponse<Alert> response = esClient.search(s -> s
                    .index(alertsIndex)
                    .from(from)
                    .size(size)
                    .query(q -> q
                            .queryString(qs -> qs
                                    .query(queryString))),
                    Alert.class);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error searching alerts", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Alert> getAlertsByTimeRange(LocalDateTime start, LocalDateTime end, int size) {
        try {
            SearchResponse<Alert> response = esClient.search(s -> s
                    .index(alertsIndex)
                    .size(size)
                    .query(q -> q
                            .range(r -> r
                                    .field("timestamp")
                                    .gte(JsonData.of(start.toInstant(ZoneOffset.UTC).toEpochMilli()))
                                    .lte(JsonData.of(end.toInstant(ZoneOffset.UTC).toEpochMilli()))))
                    .sort(so -> so
                            .field(f -> f
                                    .field("timestamp")
                                    .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc))),
                    Alert.class);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting alerts by time range", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Long> aggregateByField(String field, LocalDateTime since, int topN) {
        try {
            SearchResponse<Void> response = esClient.search(s -> s
                    .index(alertsIndex)
                    .size(0)
                    .query(q -> q
                            .range(r -> r
                                    .field("timestamp")
                                    .gte(JsonData.of(since.toInstant(ZoneOffset.UTC).toEpochMilli()))))
                    .aggregations(field, a -> a
                            .terms(t -> t
                                    .field(field + ".keyword")
                                    .size(topN))),
                    Void.class);

            Map<String, Long> result = new LinkedHashMap<>();

            if (response.aggregations() != null && response.aggregations().get(field) != null) {
                response.aggregations().get(field).sterms().buckets().array()
                        .forEach(bucket -> result.put(bucket.key().stringValue(), bucket.docCount()));
            }

            return result;
        } catch (Exception e) {
            log.error("Error aggregating by field: {}", field, e);
            return Collections.emptyMap();
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            return esClient.ping().value();
        } catch (Exception e) {
            log.warn("Elasticsearch is not available", e);
            return false;
        }
    }

    @Override
    public void createIndexIfNotExists() {
        try {
            boolean exists = esClient.indices().exists(ExistsRequest.of(e -> e.index(alertsIndex))).value();

            if (!exists) {
                CreateIndexResponse response = esClient.indices().create(c -> c
                        .index(alertsIndex)
                        .mappings(m -> m
                                .properties("timestamp", p -> p.date(d -> d))
                                .properties("sourceIp", p -> p.keyword(k -> k))
                                .properties("destIp", p -> p.keyword(k -> k))
                                .properties("severity", p -> p.keyword(k -> k))
                                .properties("category", p -> p.keyword(k -> k))
                                .properties("signature", p -> p.text(t -> t.fields("keyword", f -> f.keyword(k -> k))))
                                .properties("protocol", p -> p.keyword(k -> k))));

                log.info("Created Elasticsearch index: {}", alertsIndex);
            }
        } catch (Exception e) {
            log.error("Error creating Elasticsearch index", e);
        }
    }
}
