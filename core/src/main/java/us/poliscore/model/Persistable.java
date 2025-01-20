package us.poliscore.model;

public interface Persistable {
	
	public static final String OBJECT_BY_DATE_INDEX = "ObjectsByDate";
	
	public static final String OBJECT_BY_RATING_INDEX = "ObjectsByRating";
	
	public static final String OBJECT_BY_LOCATION_INDEX = "ObjectsByLocation";
	
	public static final String OBJECT_BY_IMPACT_INDEX = "ObjectsByImpact";
	
	public String getId();
	public void setId(String id);
	
	public String getIdClassPrefix();
	public void setIdClassPrefix(String prefix);
}
