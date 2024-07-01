package us.poliscore.service.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.model.Persistable;

@ApplicationScoped
public class MemoryPersistenceService implements PersistenceServiceIF {
	
	protected Map<String, Persistable> memoryStore = new HashMap<String,Persistable>();
	
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
	
	@SneakyThrows
	public <T extends Persistable> List<T> query(Class<T> clazz)
	{
		val idClassPrefix =(String) clazz.getField("ID_CLASS_PREFIX").get(null);
		
		return memoryStore.values().stream().filter(o -> o.getIdClassPrefix().equals(idClassPrefix)).map(o -> (T) o).toList();
	}
}
