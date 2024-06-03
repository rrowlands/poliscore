package ch.poliscore.bill;

import lombok.Data;

@Data
abstract public class BillInterpretationMetadata {
	public static enum BillInterpretationMetadataType {
		OPEN_AI;
	}
	
	abstract public BillInterpretationMetadataType getType();
}
