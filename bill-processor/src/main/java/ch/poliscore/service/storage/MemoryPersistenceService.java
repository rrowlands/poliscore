package ch.poliscore.service.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import ch.poliscore.model.Persistable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
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
	public <T extends Persistable> Optional<T> retrieve(String id, Class<T> clazz)
	{
		val key = clazz.getName() + SEPARATOR + id;
		
		if (memoryStore.containsKey(key))
		{
			return Optional.of((T) memoryStore.get(key));
		}
		else
		{
			return Optional.empty();
		}
	}
	
	public <T> long count(Class<T> clazz)
	{
		return memoryStore.keySet().stream().filter(k -> k.startsWith(clazz.getName() + SEPARATOR)).count();
	}
	
	public <T> boolean contains(String id, Class<T> clazz)
	{
		val key = clazz.getName() + SEPARATOR + id;
		
		return memoryStore.containsKey(key);
	}
}
