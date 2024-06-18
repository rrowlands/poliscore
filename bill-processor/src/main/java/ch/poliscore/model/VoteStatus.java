package ch.poliscore.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
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
