package com.flexcodelabs.flextuma.core.senders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;

import lombok.Data;

/** Client for Beem's documented delivery-report lookup API. */
@Service
public class BeemDeliveryReportClient {

    static final String DELIVERY_REPORTS_URL = "https://dlrapi.beem.africa/public/v1/delivery-reports";

    private final RestTemplate restTemplate;

    public BeemDeliveryReportClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public BeemDeliveryStatus lookup(SmsConnector connector, String recipient, String requestId) {
        String url = UriComponentsBuilder.fromUriString(DELIVERY_REPORTS_URL)
                .queryParam("dest_addr", recipient)
                .queryParam("request_id", requestId)
                .toUriString();

        ResponseEntity<List<BeemDeliveryStatus>> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<Void>(headers(connector)), new ParameterizedTypeReference<>() {
                });
        List<BeemDeliveryStatus> reports = response.getBody();
        return reports == null || reports.isEmpty() ? null : reports.get(0);
    }

    private HttpHeaders headers(SmsConnector connector) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String credentials = connector.getKey() + ":" + connector.getSecret();
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        return headers;
    }

    @Data
    public static class BeemDeliveryStatus {
        @JsonProperty("dest_addr")
        private String destinationAddress;

        @JsonProperty("request_id")
        private String requestId;

        private String status;
    }
}
