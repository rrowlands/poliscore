package us.poliscore.service.storage;

import java.util.Optional;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import us.poliscore.model.Persistable;

@ApplicationScoped
@DefaultBean
public class CachedDynamoDbService implements ApplicationDataStoreIF
{
	@Inject
	private MemoryPersistenceService memory;
	
	@Inject
	private DynamoDbPersistenceService dynamodb;

	@Override
	public void store(Persistable obj) {
		memory.store(obj);
		dynamodb.store(obj);
	}

	@Override
	public <T extends Persistable> Optional<T> retrieve(String id, Class<T> clazz)
	{
		if (memory.contains(id, clazz))
		{
			return memory.retrieve(id, clazz);
		}
		
		Optional<T> result = dynamodb.retrieve(id, clazz);
		
		if (result.isPresent())
		{
			memory.store(result.get());
		}
		
		return result;
	}
	
}