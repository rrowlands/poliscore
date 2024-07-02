package us.poliscore.model;

public enum VoteStatus {
	AYE,
	NAY,
	PRESENT,
	NOT_VOTING;
	
	private VoteStatus() {}
	
	public String describe()
	{
		if (this.equals(AYE))
		{
			return "for";
		}
		else if (this.equals(NAY))
		{
			return "against";
		}
		else if (this.equals(PRESENT))
		{
			return "present";
		}
		
		return "skip";
	}
	
	public boolean isRelevant()
	{
		return this.equals(AYE) || this.equals(NAY);
	}
}
