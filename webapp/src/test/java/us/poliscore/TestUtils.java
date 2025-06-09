package us.poliscore;

import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.legislator.Legislator;

public class TestUtils {
	public static String BERNIE_SANDERS_ID = Legislator.generateId(LegislativeNamespace.US_CONGRESS, PoliscoreUtil.CURRENT_SESSION.getNumber(), "S000033");
	
	public static String MIKE_JOHNSON_ID = Legislator.generateId(LegislativeNamespace.US_CONGRESS, PoliscoreUtil.CURRENT_SESSION.getNumber(), "J000299");
	
	public static String MITT_ROMNEY_ID = Legislator.generateId(LegislativeNamespace.US_CONGRESS, PoliscoreUtil.CURRENT_SESSION.getNumber(), "R000615");
	
	public static String JOE_BIDEN_ID = Legislator.generateId(LegislativeNamespace.US_CONGRESS, PoliscoreUtil.CURRENT_SESSION.getNumber(), "B000444");
	
	public static String CHUCK_SCHUMER_ID = Legislator.generateId(LegislativeNamespace.US_CONGRESS, PoliscoreUtil.CURRENT_SESSION.getNumber(), "S000148");
	
	public static String[] SPRINT_1_LEGISLATORS = new String[] { BERNIE_SANDERS_ID, MIKE_JOHNSON_ID, MITT_ROMNEY_ID, CHUCK_SCHUMER_ID };
}
