package ch.poliscore.bill;

import lombok.Data;

@Data
public class OpenAIInterpretationMetadata extends BillInterpretationMetadata {
	protected String model;
	
	protected int promptVersion;
	
	public BillInterpretationMetadataType getType()
	{
		return BillInterpretationMetadataType.OPEN_AI;
	}
}
