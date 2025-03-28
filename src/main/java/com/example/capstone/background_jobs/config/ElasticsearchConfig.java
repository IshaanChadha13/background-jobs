package com.example.capstone.background_jobs.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host}")
    private String esHost;

    @Value("${elasticsearch.port}")
    private int esPort;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        // Create the low-level client
        RestClient restClient = RestClient.builder(
                new HttpHost(esHost, esPort, "http")
        ).build();

        // Create the transport that uses Jackson for JSON
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

        // Build the high-level client
        return new ElasticsearchClient(transport);
    }
}
