package ch.poliscore.model;

public interface Persistable {
	public String getId();
	public void setId(String id);
	public String getIdClassPrefix();
	public void setIdClassPrefix(String prefix);
}
