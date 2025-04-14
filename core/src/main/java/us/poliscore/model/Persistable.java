package us.poliscore.model;

import lombok.SneakyThrows;

public interface Persistable {
	
	public static final String OBJECT_BY_DATE_INDEX = "ObjectsByDate";
	
	public static final String OBJECT_BY_RATING_INDEX = "ObjectsByRating";
	
	public static final String OBJECT_BY_LOCATION_INDEX = "ObjectsByLocation";
	
	public static final String OBJECT_BY_IMPACT_INDEX = "ObjectsByImpact";
	
	public static final String OBJECT_BY_ISSUE_IMPACT_INDEX = "ObjectsByIssueImpact";
	
	public static final String OBJECT_BY_ISSUE_RATING_INDEX = "ObjectsByIssueRating";
	
	public String getId();
	public void setId(String id);
	
	public String getStorageBucket();
	public void setStorageBucket(String prefix);
	
	@SneakyThrows
	public static String getClassStorageBucket(Class<?> clazz)
	{
		try { return (String) clazz.getMethod("getClassStorageBucket").invoke(null); } catch (Throwable t) {}
		
		return (String) clazz.getField("ID_CLASS_PREFIX").get(null);
	}
}
