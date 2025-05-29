package us.poliscore.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SecretService {
	@Data
	@AllArgsConstructor
	public static class UsernameAndPassword {
		String username;
		String password;
	}

	private final Map<String, String> secretCache = new ConcurrentHashMap<>();

	public String getGoogleSearchSecret() {
		return getSecret("google-search", Region.of("us-east-1"));
	}

	public String getOpenAISecret() {
		return getSecret("openai", Region.of("us-east-1"));
	}

	public String getGovInfoSecret() {
		return getSecret("govinfo.gov", Region.of("us-east-1"));
	}

	@SneakyThrows
	public UsernameAndPassword getLegiscanSecret() {
		return new ObjectMapper().readValue(getSecret("legiscan2", Region.of("us-east-1")), UsernameAndPassword.class);
	}

	private String getSecret(String secretName, Region region) {
		// Check the cache first
		if (secretCache.containsKey(secretName)) {
			return secretCache.get(secretName);
		}

		// Fetch from AWS if not cached
		SecretsManagerClient client = SecretsManagerClient.builder()
				.region(region)
				.build();

		GetSecretValueRequest request = GetSecretValueRequest.builder()
				.secretId(secretName)
				.build();

		GetSecretValueResponse response = client.getSecretValue(request);
		String secret = response.secretString();

		secretCache.put(secretName, secret);
		return secret;
	}
}
