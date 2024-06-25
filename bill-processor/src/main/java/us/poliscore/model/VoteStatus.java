package us.poliscore.model;

import us.poliscore.model.dynamodb.WorkaroundEnumAttributeConverter.VoteStatusAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

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
}
