package ch.poliscore.service;

import ch.poliscore.model.Persistable;

public interface PersistenceServiceIF
{
	public void store(Persistable obj);
	
	public <T> T retrieve(String id, Class<T> clazz);
}
