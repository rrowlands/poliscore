
package us.poliscore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.view.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class LegiscanService {
    
    private static final String BASE_URL = "https://api.legiscan.com/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    
    @Inject
    SecretService secretService;
    
    @Inject
    ObjectMapper objectMapper;
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    
    @SneakyThrows
    private String buildUrl(String endpoint, String... params) {
        StringBuilder url = new StringBuilder(BASE_URL)
                .append("?key=").append(secretService.getLegiscanSecret())
                .append("&op=").append(endpoint);
        
        for (int i = 0; i < params.length; i += 2) {
            if (i + 1 < params.length) {
                url.append("&").append(params[i]).append("=")
                   .append(URLEncoder.encode(params[i + 1], StandardCharsets.UTF_8));
            }
        }
        
        return url.toString();
    }
    
    @SneakyThrows
    private <T> Optional<T> makeRequest(String url, TypeReference<T> typeRef) {
        try {
            Log.debug("Making Legiscan API request to: " + url);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                T result = objectMapper.readValue(response.body(), typeRef);
                return Optional.of(result);
            } else {
                Log.error("Legiscan API request failed with status: " + response.statusCode() 
                         + ", body: " + response.body());
                return Optional.empty();
            }
            
        } catch (Exception e) {
            Log.error("Error making Legiscan API request", e);
            return Optional.empty();
        }
    }
    
    /**
     * Get a specific bill by bill ID
     */
    public Optional<LegiscanBillView> getBill(String billId) {
        String url = buildUrl("getBill", "id", billId);
        return makeRequest(url, new TypeReference<LegiscanResponse<LegiscanBillView>>() {})
                .map(response -> response.getBill());
    }
    
    /**
     * Get bills for a specific session
     */
    public Optional<List<LegiscanBillView>> getBills(String sessionId) {
        String url = buildUrl("getBillsBySession", "id", sessionId);
        return makeRequest(url, new TypeReference<LegiscanResponse<List<LegiscanBillView>>>() {})
                .map(response -> response.getBills());
    }
    
    /**
     * Search bills by query
     */
    public Optional<List<LegiscanBillView>> searchBills(String query, String state, String year) {
        String url = buildUrl("search", 
                "query", query,
                "state", state,
                "year", year);
        return makeRequest(url, new TypeReference<LegiscanResponse<List<LegiscanBillView>>>() {})
                .map(response -> response.getBills());
    }
    
    /**
     * Get a specific legislator by people ID
     */
    public Optional<LegiscanLegislatorView> getLegislator(String peopleId) {
        String url = buildUrl("getPerson", "id", peopleId);
        return makeRequest(url, new TypeReference<LegiscanResponse<LegiscanLegislatorView>>() {})
                .map(response -> response.getPerson());
    }
    
    /**
     * Get legislators for a specific session
     */
    public Optional<List<LegiscanLegislatorView>> getLegislators(String sessionId) {
        String url = buildUrl("getPeopleBySession", "id", sessionId);
        return makeRequest(url, new TypeReference<LegiscanResponse<List<LegiscanLegislatorView>>>() {})
                .map(response -> response.getPeople());
    }
    
    /**
     * Get a specific roll call by roll call ID
     */
    public Optional<LegiscanRollCallView> getRollCall(String rollCallId) {
        String url = buildUrl("getRollCall", "id", rollCallId);
        return makeRequest(url, new TypeReference<LegiscanResponse<LegiscanRollCallView>>() {})
                .map(response -> response.getRollcall());
    }
    
    /**
     * Get roll calls for a specific bill
     */
    public Optional<List<LegiscanRollCallView>> getRollCallsByBill(String billId) {
        String url = buildUrl("getRollCallsByBill", "id", billId);
        return makeRequest(url, new TypeReference<LegiscanResponse<List<LegiscanRollCallView>>>() {})
                .map(response -> response.getRollcalls());
    }
    
    /**
     * Get available sessions for a state
     */
    public Optional<List<LegiscanSessionView>> getSessions(String state) {
        String url = buildUrl("getSessionList", "state", state);
        return makeRequest(url, new TypeReference<LegiscanResponse<List<LegiscanSessionView>>>() {})
                .map(response -> response.getBills()); // Sessions are returned in bills field
    }
    
    /**
     * Get bill text by doc ID
     */
    @SneakyThrows
    public Optional<String> getBillText(String docId) {
        String url = buildUrl("getBillText", "id", docId);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        
        try {
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return Optional.of(response.body());
            } else {
                Log.error("Failed to get bill text, status: " + response.statusCode());
                return Optional.empty();
            }
        } catch (Exception e) {
            Log.error("Error getting bill text", e);
            return Optional.empty();
        }
    }
}
