package ch.poliscore;

public enum VoteStatus {
	AYE("for"),
	NAY("against"),
	PRESENT("present"),
	NOT_VOTING("skip");
	
	private String description;
	
	private VoteStatus(String description)
	{
		this.description = description;
	}
	
	public String describe()
	{
		return this.description;
	}
}
