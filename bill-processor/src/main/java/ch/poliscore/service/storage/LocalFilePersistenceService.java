package ch.poliscore.service.storage;

import java.io.File;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.PoliscoreUtil;
import ch.poliscore.model.Persistable;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import lombok.SneakyThrows;

@ApplicationScoped
public class LocalFilePersistenceService implements PersistenceServiceIF
{

	protected File getLocalStorage()
	{
//		return new File(Environment.getDeployedPath(), "../store");
		
		return new File(PoliscoreUtil.APP_DATA, "store");
	}
	
	public File getStore(Class<?> clazz)
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
	
	@SneakyThrows
	public void store(Persistable obj) {
		File storage = getStore(obj.getClass());
		File out = new File(storage, obj.getId() + ".json");
		
		var mapper = new ObjectMapper();
		mapper.writerWithDefaultPrettyPrinter().writeValue(out, obj);
		
//		Log.info("Wrote file to " + out.getAbsolutePath());
	}

	@SneakyThrows
	public <T extends Persistable> Optional<T> retrieve(String id, Class<T> clazz)
	{
		File billStorage = getStore(clazz);
		File stored = new File(billStorage, id + ".json");
		
		if (!stored.exists())
			return Optional.empty();
		
		var mapper = new ObjectMapper();
		return Optional.of(mapper.readValue(stored, clazz));
	}
	
}