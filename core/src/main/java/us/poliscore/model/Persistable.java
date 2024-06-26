package us.poliscore.model;

public interface Persistable {
	
	public static final String OBJECT_CLASS_INDEX = "ObjectClass";
	
	public String getId();
	public void setId(String id);
	public String getIdClassPrefix();
	public void setIdClassPrefix(String prefix);
}
