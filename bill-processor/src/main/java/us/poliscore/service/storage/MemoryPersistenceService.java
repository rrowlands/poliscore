package us.poliscore.service.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import us.poliscore.model.Persistable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import lombok.val;

@ApplicationScoped
public class MemoryPersistenceService implements PersistenceServiceIF {
	
	protected Map<String, Object> memoryStore = new HashMap<String,Object>();
	
	public void store(Persistable obj)
	{
		memoryStore.put(obj.getId(), obj);
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Persistable> Optional<T> retrieve(String id, Class<T> clazz)
	{
		if (memoryStore.containsKey(id))
		{
			return Optional.of((T) memoryStore.get(id));
		}
		else
		{
			return Optional.empty();
		}
	}
	
	public <T extends Persistable> long count(String idClassPrefix)
	{
		return memoryStore.keySet().stream().filter(k -> k.startsWith(idClassPrefix)).count();
	}
	
	public <T> boolean contains(String id, Class<T> clazz)
	{
		return memoryStore.containsKey(id);
	}
}
