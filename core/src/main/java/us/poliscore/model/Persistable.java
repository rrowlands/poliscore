package us.poliscore.model;

import java.time.LocalDate;

public interface Persistable {
	
	public static final String OBJECT_BY_DATE_INDEX = "ObjectsByDate";
	
	public static final String OBJECT_BY_RATING_INDEX = "ObjectsByRating";
	
	public static final String OBJECT_BY_LOCATION_INDEX = "ObjectsByLocation";
	
	public String getId();
	public void setId(String id);
	
	public String getIdClassPrefix();
	public void setIdClassPrefix(String prefix);
	
	public LocalDate getDate();
	public void setDate(LocalDate date);
	
	public int getRating();
	public void setRating(int rating);
}
