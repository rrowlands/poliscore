package ch.poliscore.service;

import ch.poliscore.DataNotFoundException;
import ch.poliscore.model.Persistable;

public interface PersistenceServiceIF
{
	public void store(Persistable obj);
	
	public <T extends Persistable> T retrieve(String id, Class<T> clazz) throws DataNotFoundException;
}
