package ch.poliscore.service.storage;

import java.util.Optional;

import ch.poliscore.model.Persistable;

public interface PersistenceServiceIF
{
	public <T extends Persistable> void store(T obj);
	
	public <T extends Persistable> Optional<T> retrieve(String id, Class<T> clazz);
}
