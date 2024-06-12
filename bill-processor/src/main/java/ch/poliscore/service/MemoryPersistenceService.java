package ch.poliscore.service;

import java.util.HashMap;
import java.util.Map;

import ch.poliscore.model.Persistable;
import jakarta.enterprise.context.ApplicationScoped;

//@ApplicationScoped
public class MemoryPersistenceService implements PersistenceServiceIF {
	protected Map<String, Object> memoryStore = new HashMap<String,Object>();
	
	public void store(Persistable obj)
	{
		memoryStore.put(obj.getId(), obj);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T retrieve(String id, Class<T> clazz)
	{
		if (memoryStore.containsKey(id))
		{
			return (T) memoryStore.get(id);
		}
		else
		{
			throw new RuntimeException("Object with id [" + id + "] not found.");
		}
	}
}
