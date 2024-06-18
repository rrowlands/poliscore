package ch.poliscore.service.storage;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.model.Persistable;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ApplicationScoped
public class S3PersistenceService implements PersistenceServiceIF
{
	
	public static final String BUCKET_NAME = "poliscore-prod";
	
	protected String getKey(String id, Class<?> clazz)
	{
		return clazz.getSimpleName() + "/" + id + ".json";
	}
	
	private S3Client client;
	
	private S3Client getClient()
	{
		if (client == null)
		{
			client = S3Client.builder()
	                .build();
		}
		
		return client;
	}
	
	@SneakyThrows
	public void store(Persistable obj)
	{
		val key = getKey(obj.getId(), obj.getClass());
		
        PutObjectRequest putOb = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .build();

        getClient().putObject(putOb, RequestBody.fromString(new ObjectMapper().writeValueAsString(obj)));
        
        Log.info("Uploaded to S3 " + key);
	}
	
	@SneakyThrows
	public <T extends Persistable> Optional<T> retrieve(String id, Class<T> clazz)
	{
		val key = getKey(id, clazz);
		
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .build();

        try {
        	@Cleanup val resp = getClient().getObject(req);
        	
        	Log.info("Retrieved " + clazz.getSimpleName() + " from S3 " + key);
        	
        	return Optional.of(new ObjectMapper().readValue(resp, clazz));
        }
        catch (NoSuchKeyException ex)
        {
        	Log.info(clazz.getSimpleName() + " not found on S3 " + key);
        	
        	return Optional.empty();
        }
	}
	
	public <T> boolean exists(String id, Class<T> clazz)
	{
		val key = getKey(id, clazz);
		
		try
		{
			val resp = getClient().headObject(HeadObjectRequest.builder()
					.bucket(BUCKET_NAME)
					.key(key)
					.build());
			
			return true;
		}
		catch (NoSuchKeyException ex)
		{
			return false;
		}
	}
	
}
