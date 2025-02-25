package com.example.capstone.background_jobs.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateByQueryResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.example.capstone.background_jobs.model.Findings;
import com.example.capstone.background_jobs.model.TenantEntity;
import com.example.capstone.background_jobs.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class ElasticsearchClientService {

    private final ElasticsearchClient esClient;
    private final TenantRepository tenantRepository;

    public ElasticsearchClientService(ElasticsearchClient esClient, TenantRepository tenantRepository) {
        this.esClient = esClient;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Update the 'state' field for documents matching the given 'alertNumber' in 'esIndex'.
     * This uses an UpdateByQuery approach with a painless script:
     *   ctx._source.state = params.newState
     *
     * @param esIndex     The Elasticsearch index (e.g. "tenant-1")
     * @param alertNumber The unique alertNumber to match on
     * @param newState    e.g. "OPEN", "DISMISS", "RESOLVE", etc.
     */
    public void updateFindingInEs(String esIndex, long alertNumber, String newState) throws IOException {
        UpdateByQueryResponse response = esClient.updateByQuery(r -> r
                .index(esIndex)
                .query(q -> q
                        .term(t -> t
                                .field("alertNumber")
                                .value(alertNumber)
                        )
                )
                .script(s -> s
                        .source("ctx._source.state = params.newState")
                        .lang("painless")
                        .params(Collections.singletonMap("newState", JsonData.of(newState)))
                )
        );

        System.out.printf("[ElasticsearchClientService] updateFindingInEs => matched=%d, updated=%d, failures=%d%n",
                response.total(), response.updated(), response.failures().size());

        if (!response.failures().isEmpty()) {
            throw new RuntimeException("ES update failures => " + response.failures());
        }


    }

    public Optional<String> findFindingIdByAlertNumber(String esIndex, long alertNumber) throws IOException {
        SearchResponse<Findings> searchResponse = esClient.search(s -> s
                        .index(esIndex)
                        .query(q -> q.term(t -> t.field("alertNumber").value(alertNumber))),
                Findings.class
        );

        List<Hit<Findings>> hits = searchResponse.hits().hits();
        if (!hits.isEmpty()) {
            Findings found = hits.get(0).source();
            if (found != null) {
                return Optional.ofNullable(found.getId());
            }
        }
        return Optional.empty();
    }

    public void updateFindingTicketId(Long tenantId, String findingId, String newTicketKey) {
        try {
            // 1. Fetch the tenant’s esIndex from the DB:
            Optional<TenantEntity> tenantOpt = tenantRepository.findById(tenantId);
            if (tenantOpt.isEmpty()) {
                throw new IllegalStateException("No tenant found with id=" + tenantId);
            }
            TenantEntity tenant = tenantOpt.get();

            String esIndex = tenant.getEsIndex();
            if (esIndex == null || esIndex.isBlank()) {
                throw new IllegalStateException("Tenant " + tenantId + " has no valid esIndex set.");
            }

            // 2. Build partial doc with only the ticketId field
            Map<String, Object> partialDoc = new HashMap<>();
            partialDoc.put("ticketId", newTicketKey);

            // 3. Construct the UpdateRequest for a partial update
            UpdateRequest<Map<String, Object>, Map<String, Object>> updateReq =
                    UpdateRequest.of(u -> u
                            .index(esIndex)    // the index for this tenant
                            .id(findingId)     // the doc ID (i.e., the existing finding's _id)
                            .doc(partialDoc)   // only updating ticketId
                    );

            // 4. Execute the update
            UpdateResponse<Map<String, Object>> response = esClient.update(updateReq, Map.class);
            System.out.println("Successfully updated ticketId for doc ID = " + findingId
                    + " in index = " + esIndex + ", result: " + response.result());

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to update ticketId in Elasticsearch", e);
        }
    }
}
