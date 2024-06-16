package ch.poliscore.service.storage;

import java.util.Optional;

import ch.poliscore.model.Persistable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

@ApplicationScoped
@Alternative
public class CachedDynamoDbService implements ApplicationDataStoreIF
{
	@Inject
	private MemoryPersistenceService memory;
	
	@Inject
	private DynamoDBPersistenceService dynamodb;

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
		
		return dynamodb.retrieve(id, clazz);
	}
	
}
