package us.poliscore.service.storage;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.Persistable;

@ApplicationScoped
public class LocalFilePersistenceService implements PersistenceServiceIF
{

	protected File getLocalStorage()
	{
//		return new File(Environment.getDeployedPath(), "../store");
		
		return new File(PoliscoreUtil.APP_DATA, "store");
	}
	
	public File getStore(String idClassPrefix)
	{
//		if (Bill.class.equals(clazz)) return new File(getLocalStorage(), "bills");
//		else if (BillInterpretation.class.equals(clazz)) return new File(getLocalStorage(), "interpretations");
//		else if (Legislator.class.equals(clazz)) return new File(getLocalStorage(), "legislators");
//		else return new File(getLocalStorage(), clazz.getName());
		
		File f = new File(getLocalStorage(), idClassPrefix);
		
		if (!f.exists())
		{
			f.mkdirs();
		}
		
		return f;
	}
	
	@SneakyThrows
	public void put(Persistable obj) {
		File storage = getStore(obj.getIdClassPrefix());
		File out = new File(storage, obj.getId() + ".json");
		
		var mapper = PoliscoreUtil.getObjectMapper();
		mapper.writerWithDefaultPrettyPrinter().writeValue(out, obj);
		
//		Log.info("Wrote file to " + out.getAbsolutePath());
	}

	@SneakyThrows
	public <T extends Persistable> Optional<T> get(String id, Class<T> clazz)
	{
		File objectStore = getStore(id.substring(0, 3));
		File stored = new File(objectStore, id + ".json");
		
		if (!stored.exists())
			return Optional.empty();
		
		var mapper = PoliscoreUtil.getObjectMapper();
		return Optional.of(mapper.readValue(stored, clazz));
	}

	@Override
	public <T extends Persistable> boolean exists(String id, Class<T> clazz) {
		File objectStore = getStore(id.substring(0, 3));
		File stored = new File(objectStore, id + ".json");
		return stored.exists();
	}
	
	@Override
	@SneakyThrows
	public <T extends Persistable> List<T> query(Class<T> clazz) {
		val idClassPrefix =(String) clazz.getField("ID_CLASS_PREFIX").get(null);
		File objectStore = getStore(idClassPrefix);
		
		val mapper = PoliscoreUtil.getObjectMapper();
		return Arrays.asList(objectStore.listFiles()).stream().map(f -> {
			try {
				return mapper.readValue(FileUtils.readFileToString(f, "UTF-8"), clazz);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}).toList();
	}
	
}
