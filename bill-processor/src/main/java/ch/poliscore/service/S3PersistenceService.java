package ch.poliscore.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.model.Persistable;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.SneakyThrows;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ApplicationScoped
public class S3PersistenceService { // implements PersistenceServiceIF
	
	public static final String BUCKET_NAME = "poliscore-prod";
	
	@SneakyThrows
	public void store(Persistable obj)
	{
		S3Client client = S3Client.builder()
                .build();
		
        PutObjectRequest putOb = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(obj.getId() + ".json")
                .build();

        client.putObject(putOb, RequestBody.fromString(new ObjectMapper().writeValueAsString(obj)));
        
        System.out.println("Successfully placed " + obj.getId() + " into bucket " + BUCKET_NAME);
	}
	
	public <T extends Persistable> T retrieve(String id, Class<T> clazz)
	{
		throw new UnsupportedOperationException();
	}
	
}
