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
public class LocalFilePersistenceService implements ObjectStorageServiceIF
{

	protected File getLocalStorage()
	{
//		return new File(Environment.getDeployedPath(), "../store");
		
		return new File(PoliscoreUtil.APP_DATA, "store");
	}
	
	protected File getStore(String idClassPrefix)
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
	
	protected File fileFor(String id) {
		File f = new File(getLocalStorage(), id + ".json");
		
		if (!f.getParentFile().exists()) {
			f.getParentFile().mkdirs();
		}
		
		return f;
	}
	
	@SneakyThrows
	public void put(Persistable obj) {
		File f = fileFor(obj.getId());
		
		var mapper = PoliscoreUtil.getObjectMapper();
		mapper.writerWithDefaultPrettyPrinter().writeValue(f, obj);
		
//		Log.info("Wrote file to " + out.getAbsolutePath());
	}

	@SneakyThrows
	public <T extends Persistable> Optional<T> get(String id, Class<T> clazz)
	{
		File f = fileFor(id);
		
		if (!f.exists())
			return Optional.empty();
		
		var mapper = PoliscoreUtil.getObjectMapper();
		return Optional.of(mapper.readValue(f, clazz));
	}

	@Override
	public <T extends Persistable> boolean exists(String id, Class<T> clazz) {
		File f = fileFor(id);
		return f.exists();
	}
	
	@Override
	@SneakyThrows
	public <T extends Persistable> List<T> query(Class<T> clazz) {
//		val idClassPrefix =(String) clazz.getField("ID_CLASS_PREFIX").get(null);
//		File objectStore = getStore(idClassPrefix);
//		
//		val mapper = PoliscoreUtil.getObjectMapper();
//		return Arrays.asList(objectStore.listFiles()).stream().map(f -> {
//			try {
//				return mapper.readValue(FileUtils.readFileToString(f, "UTF-8"), clazz);
//			} catch (IOException e) {
//				throw new RuntimeException(e);
//			}
//		}).toList();
		
		throw new UnsupportedOperationException();
	}

	@Override
	public <T extends Persistable> List<T> query(Class<T> clazz, String storageBucket) {
		throw new UnsupportedOperationException();
	}
	
}
