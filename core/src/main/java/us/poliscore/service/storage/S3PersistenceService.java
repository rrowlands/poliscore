package us.poliscore.service.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.Persistable;

@ApplicationScoped
public class S3PersistenceService implements ObjectStorageServiceIF
{
	
	public static final String BUCKET_NAME = "poliscore-archive";
	
	private S3Client client;
	
	private static HashMap<String, Set<String>> objectsInBucket = new HashMap<String, Set<String>>();
	
	protected String getKey(String id)
	{
		return id + ".json";
	}
	
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
	public void put(Persistable obj)
	{
		val key = getKey(obj.getId());
		
        PutObjectRequest putOb = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .build();

        getClient().putObject(putOb, RequestBody.fromString(PoliscoreUtil.getObjectMapper().writeValueAsString(obj)));
        
        Log.info("Uploaded to S3 " + key);
	}
	
	@SneakyThrows
	public <T extends Persistable> Optional<T> get(String id, Class<T> clazz)
	{
		val key = getKey(id);
		
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .build();

        try {
        	@Cleanup val resp = getClient().getObject(req);
        	
//        	Log.info("Retrieved " + clazz.getSimpleName() + " from S3 " + key);
        	
        	return Optional.of(PoliscoreUtil.getObjectMapper().readValue(resp, clazz));
        }
        catch (NoSuchKeyException ex)
        {
//        	Log.info(clazz.getSimpleName() + " not found on S3 " + key);
        	
        	return Optional.empty();
        }
	}
	
	@Override
	@SneakyThrows
	public <T extends Persistable> boolean exists(String id, Class<T> clazz)
	{
		val idClassPrefix = (String) clazz.getField("ID_CLASS_PREFIX").get(null);
		if (objectsInBucket.containsKey(idClassPrefix)) return objectsInBucket.get(idClassPrefix).contains(id);
		
		val key = getKey(id);
		
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
	
	@Override
	public <T extends Persistable> List<T> query(Class<T> clazz) {
		throw new UnsupportedOperationException();
	}
	
	@SneakyThrows
	public <T extends Persistable> void optimizeExists(Class<T> clazz) {
		val idClassPrefix = (String) clazz.getField("ID_CLASS_PREFIX").get(null);
		objectsInBucket.put(idClassPrefix, new HashSet<String>());
		
		String continuationToken = null;
		do {
			val builder = ListObjectsV2Request.builder().bucket(BUCKET_NAME)
					.prefix(idClassPrefix);
			
			if (continuationToken != null) {
				builder.continuationToken(continuationToken);
			}
			
			val resp = getClient().listObjectsV2(builder.build());
			
			objectsInBucket.get(idClassPrefix).addAll(resp.contents().stream().map(o -> FilenameUtils.getPath(o.key()) + FilenameUtils.getBaseName(o.key())).toList());
			
			continuationToken = resp.nextContinuationToken();
		}
		while(continuationToken != null);
	}
	
}
