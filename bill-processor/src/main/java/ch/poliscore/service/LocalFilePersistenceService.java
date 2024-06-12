package ch.poliscore.service;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.Environment;
import ch.poliscore.model.Persistable;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.SneakyThrows;

@ApplicationScoped
public class LocalFilePersistenceService implements PersistenceServiceIF
{

	protected File getLocalStorage()
	{
		return new File(Environment.getDeployedPath(), "../store");
	}
	
	public File getStore(Class clazz)
	{
//		if (Bill.class.equals(clazz)) return new File(getLocalStorage(), "bills");
//		else if (BillInterpretation.class.equals(clazz)) return new File(getLocalStorage(), "interpretations");
//		else if (Legislator.class.equals(clazz)) return new File(getLocalStorage(), "legislators");
//		else return new File(getLocalStorage(), clazz.getName());
		
		File f = new File(getLocalStorage(), clazz.getSimpleName());
		
		if (!f.exists())
		{
			f.mkdirs();
		}
		
		return f;
	}
	
	@Override
	@SneakyThrows
	public void store(Persistable obj) {
		File storage = getStore(obj.getClass());
		File out = new File(storage, obj.getId() + ".json");
		
		var mapper = new ObjectMapper();
		mapper.writerWithDefaultPrettyPrinter().writeValue(out, obj);
		
		Log.info("Wrote file to " + out.getAbsolutePath());
	}

	@Override
	@SneakyThrows
	public <T> T retrieve(String id, Class<T> clazz) {
		File billStorage = getStore(clazz);
		File stored = new File(billStorage, id + ".json");
		
		if (!stored.exists()) throw new RuntimeException("Could not find " + clazz.getName() + " with id " + id);
		
		var mapper = new ObjectMapper();
		return mapper.readValue(stored, clazz);
	}
	
}
