package us.poliscore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;

@Data
@DynamoDbBean
@NoArgsConstructor
@AllArgsConstructor
public class InterpretationOrigin {
	public static final InterpretationOrigin POLISCORE = new InterpretationOrigin("https://poliscore.us", "PoliScore");
	
	public String url;
	
	public String title;
	
	@DynamoDbIgnore
	@JsonIgnore
	public String getIdHash() {
        try {
            // Parse the URL to extract the host
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();

            // If URI doesn't have a host, try extracting manually
            if (host == null) {
                // Fallback: remove protocol and split by '/'
                String temp = url.replaceAll("^https?://", "");
                int slashIndex = temp.indexOf('/');
                host = (slashIndex == -1) ? temp : temp.substring(0, slashIndex);
            }

            // Remove 'www.' prefix if present
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }

            // Split by '.' and take the first part of the domain
            // Example: "example.com" -> "example"
            int dotIndex = host.indexOf('.');
            String domainPart = (dotIndex == -1) ? host : host.substring(0, dotIndex);

            // Keep only alphanumeric characters
            String cleaned = domainPart.replaceAll("[^A-Za-z0-9]", "");

            // Limit to max 6 characters
            if (cleaned.length() > 6) {
                cleaned = cleaned.substring(0, 6);
            }

            // Return the cleaned "hash"
            return cleaned.toLowerCase();
        } catch (Exception e) {
            // If something goes wrong, return a fallback
        	e.printStackTrace();
            return "unknown";
        }
    }
}
