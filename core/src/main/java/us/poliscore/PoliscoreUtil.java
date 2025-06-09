package us.poliscore;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import us.poliscore.model.CongressionalSession;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.legislator.Legislator;

public class PoliscoreUtil {
	
	public static File USC_DATA = new File("/Users/rrowlands/dev/projects/congress/data");
	
	public static File APP_DATA = new File(System.getProperty("user.home") + "/appdata/poliscore");
	{
		APP_DATA.mkdirs();
	}
	
	public static String DEPLOYMENT_YEAR = "2026";
	
	public static CongressionalSession CURRENT_SESSION = CongressionalSession.of((int)Math.floor((Integer.parseInt(DEPLOYMENT_YEAR) - 1789) / 2) + 1);
	
	public static List<CongressionalSession> SUPPORTED_CONGRESSES = Arrays.asList(CongressionalSession.S118, CongressionalSession.S119);
	
	public static List<File> allFilesWhere(File parent, Predicate<File> criteria)
	{
		List<File> all = new ArrayList<File>();
		
		if (!parent.isDirectory()) return all;
		
		for (File child : parent.listFiles())
		{
			if (child.isDirectory())
			{
				all.addAll(allFilesWhere(child, criteria));
			}
			else if (criteria.test(child))
			{
				all.add(child);
			}
		}
		
		return all;
	}
	
	public static ObjectMapper getObjectMapper() { return JsonMapper.builder().findAndAddModules().build(); }
	
}
