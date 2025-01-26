package us.poliscore.service.storage;

import java.util.List;
import java.util.Optional;

import us.poliscore.PoliscoreUtil;
import us.poliscore.model.Persistable;

public interface ObjectStorageServiceIF
{
	public <T extends Persistable> Optional<T> get(String id, Class<T> clazz);
	
	public <T extends Persistable> void put(T obj);
	
	public <T extends Persistable> boolean exists(String id, Class<T> clazz);
	
	/**
	 * Queries the objects within the object's default 'classStorageBucket' which may behind the scenes utilize PoliscoreUtil.CURRENT_SESSION
	 * 
	 * @param <T>
	 * @param clazz
	 * @return
	 */
	public <T extends Persistable> List<T> query(Class<T> clazz);
	
	/**
	 * Queries the objects within the specified storage bucket.
	 * 
	 * @param <T>
	 * @param clazz
	 * @return
	 */
	public <T extends Persistable> List<T> query(Class<T> clazz, String storageBucket);
}
