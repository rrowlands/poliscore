package us.poliscore.service.storage;

import java.util.List;
import java.util.Optional;

import us.poliscore.model.Persistable;

public interface PersistenceServiceIF
{
	public <T extends Persistable> Optional<T> get(String id, Class<T> clazz);
	
	public <T extends Persistable> void put(T obj);
	
	public <T extends Persistable> boolean exists(String id, Class<T> clazz);
	
	public <T extends Persistable> List<T> query(Class<T> clazz);
}
