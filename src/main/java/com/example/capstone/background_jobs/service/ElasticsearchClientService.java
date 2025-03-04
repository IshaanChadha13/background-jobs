package com.example.capstone.background_jobs.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
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

    public void updateFindingInEs(String esIndex, long alertNumber, String newState) throws IOException {
        UpdateByQueryResponse response = esClient.updateByQuery(r -> r
                .index(esIndex)
                .conflicts(Conflicts.Proceed)
                .query(q -> q
                        .term(t -> t
                                .field("alertNumber.keyword")
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

    public Optional<String> findFindingIdByAlertNumber(String esIndex, long alertNumber, String toolType) throws IOException {
        SearchResponse<Findings> searchResponse = esClient.search(s -> s
                        .index(esIndex)
                        .query(q -> q
                                .bool(b -> b
                                        .must(m1 -> m1
                                                .term(t -> t
                                                        .field("alertNumber.keyword")
                                                        .value(alertNumber)
                                                )
                                        )
                                        .must(m2 -> m2
                                                .term(t -> t
                                                        .field("toolType.keyword")
                                                        .value(toolType)  // exact match on tool type
                                                )
                                        )
                                )
                        ),
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
                            .index(esIndex)
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

    public List<Findings> fetchFindingsByIds(Long tenantId, List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 1) Determine the tenant’s Elasticsearch index
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("No tenant found with id=" + tenantId));
        String esIndex = tenant.getEsIndex();
        if (esIndex == null || esIndex.isBlank()) {
            throw new IllegalStateException("Tenant " + tenantId + " has no valid esIndex set.");
        }

        try {
            // 2) Search by doc IDs (the actual _id in Elasticsearch)
            // The .ids(...) query is a convenient way to do an “_id in [list]” search.
            SearchResponse<Findings> response = esClient.search(
                    s -> s.index(esIndex)
                            .size(docIds.size())
                            .query(q -> q.ids(i -> i.values(docIds))),
                    Findings.class
            );

            // 3) Collect the sources
            List<Findings> results = new ArrayList<>();
            for (Hit<Findings> hit : response.hits().hits()) {
                Findings found = hit.source();
                if (found != null) {
                    results.add(found);
                }
            }
            return results;

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to fetch findings by IDs from Elasticsearch", e);
        }
    }
}
