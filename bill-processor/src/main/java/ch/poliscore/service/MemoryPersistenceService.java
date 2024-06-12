package ch.poliscore.service;

import java.util.HashMap;
import java.util.Map;

import ch.poliscore.DataNotFoundException;
import ch.poliscore.model.Persistable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.val;

@ApplicationScoped
public class MemoryPersistenceService implements PersistenceServiceIF {
	
	private static final String SEPARATOR = "~~~~";
	
	protected Map<String, Object> memoryStore = new HashMap<String,Object>();
	
	public void store(Persistable obj)
	{
		memoryStore.put(obj.getClass().getName() + SEPARATOR + obj.getId(), obj);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T retrieve(String id, Class<T> clazz)
	{
		val key = clazz.getName() + SEPARATOR + id;
		
		if (memoryStore.containsKey(key))
		{
			return (T) memoryStore.get(key);
		}
		else
		{
			throw new DataNotFoundException("Object with id [" + id + "] not found.");
		}
	}
}
