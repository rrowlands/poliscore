package us.poliscore.service;

import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;	

@ApplicationScoped
public class SecretService {
	public String getGoogleSearchSecret()
	{
		return getSecret("google-search", Region.of("us-east-1"));
	}
	
	public String getOpenAISecret() {
		return getSecret("openai", Region.of("us-east-1"));
	}
	
	public String getGovInfoSecret() {
		return getSecret("govinfo.gov", Region.of("us-east-1"));
	}
	
	private String getSecret(String secretName, Region region)
	{
		// Create a Secrets Manager client
	    SecretsManagerClient client = SecretsManagerClient.builder()
	            .region(region)
	            .build();

	    GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
	            .secretId(secretName)
	            .build();

	    GetSecretValueResponse getSecretValueResponse;

	    try {
	        getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
	    } catch (Exception e) {
	        // For a list of exceptions thrown, see
	        // https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_GetSecretValue.html
	        throw e;
	    }

	    String secret = getSecretValueResponse.secretString();

	    return secret;
	}
}