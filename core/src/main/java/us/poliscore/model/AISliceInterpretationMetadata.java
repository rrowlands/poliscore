package us.poliscore.model;

import java.time.LocalDate;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import us.poliscore.model.bill.BillSlice;

@Data
@DynamoDbBean
public class AISliceInterpretationMetadata extends AIInterpretationMetadata {
	
	protected String start;
	
	protected String end;
	
	protected int sliceIndex;
	
	public static AISliceInterpretationMetadata construct(String provider, String model, int version, BillSlice slice)
	{
		AISliceInterpretationMetadata meta = new AISliceInterpretationMetadata();
		meta.setProvider(provider);
		meta.setModel(model);
		meta.setPromptVersion(version);
		meta.setStart(slice.getStart());
		meta.setEnd(slice.getEnd());
		meta.setSliceIndex(slice.getSliceIndex());
		meta.setDate(LocalDate.now());
		return meta;
	}
	
}
