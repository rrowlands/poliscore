package ch.poliscore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class PoliscoreUtil {
	
	public static File USC_DATA = new File("/Users/rrowlands/dev/projects/congress/data");
	
	public static File APP_DATA = new File(System.getProperty("user.home") + "/appdata/poliscore");
	{
		APP_DATA.mkdirs();
	}
	
	public static int OLDEST_SUPPORTED_CONGRESS = 113;
	
	public static String BERNIE_SANDERS_ID = "S000033";
	
	public static String MIKE_JOHNSON_ID = "J000299";
	
	public static String MITT_ROMNEY_ID = "R000615";
	
	public static String JOE_BIDEN_ID = "B000444";
	
	public static String[] SPRINT_1_LEGISLATORS = new String[] { BERNIE_SANDERS_ID }; // MIKE_JOHNSON_ID, MITT_ROMNEY_ID, JOE_BIDEN_ID
	
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
	
}
