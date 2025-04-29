package us.poliscore.service.storage;

import java.util.List;
import java.util.Optional;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.val;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import us.poliscore.model.Persistable;

@ApplicationScoped
@DefaultBean
public class LocalCachedS3Service implements ApplicationDataStoreIF
{
	@Inject
	private MemoryObjectService memory;
	
	@Inject
	private S3PersistenceService s3;
	
	@Inject
	private LocalFilePersistenceService local;

	@Override
	public void put(Persistable obj) {
		memory.put(obj);
		local.put(obj);
		s3.put(obj);
	}

	@Override
	public <T extends Persistable> Optional<T> get(String id, Class<T> clazz)
	{
		if (memory.exists(id, clazz))
		{
			return memory.get(id, clazz);
		}
		
		if (local.exists(id, clazz))
		{
			return local.get(id, clazz);
		}
		
		Optional<T> result = s3.get(id, clazz);
		
		if (result.isPresent())
		{
			memory.put(result.get());
			local.put(result.get());
		}
		
		return result;
	}
	
	@Override
	public <T extends Persistable> boolean exists(String id, Class<T> clazz)
	{
		return memory.exists(id, clazz) || local.exists(id, clazz) || s3.exists(id, clazz);
	}

	@Override
	public <T extends Persistable> List<T> query(Class<T> clazz) {
		return s3.query(clazz);
	}
	
	@Override
	public <T extends Persistable> List<T> query(Class<T> clazz, String storageBucket) {
		return s3.query(clazz, storageBucket);
	}
	
	public <T extends Persistable> List<T> query(Class<T> clazz, String storageBucket, String key) {
		return s3.query(clazz, storageBucket, key, -1, true);
	}
	
	public <T extends Persistable> List<T> query(Class<T> clazz, String storageBucket, String key, int pageSize, boolean ascending) {
		return s3.query(clazz, storageBucket, key, pageSize, ascending);
	}
	
	public <T extends Persistable> void optimizeExists(Class<T> clazz) {
		s3.optimizeExists(clazz);
	}
	
	public <T extends Persistable> void clearExistsOptimize(Class<T> clazz) {
		s3.clearExistsOptimize(clazz);
	}
	
	public <T extends Persistable> void delete(String id, Class<T> clazz)
	{
		s3.delete(id, clazz);
	}
	
}
