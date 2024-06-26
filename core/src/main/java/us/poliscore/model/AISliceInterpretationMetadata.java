package us.poliscore.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import us.poliscore.model.DynamoDbJacksonAttributeConverter.DynamoDbJacksonStringifiedConverterProvider;
import us.poliscore.model.bill.BillSlice;

@Data
@DynamoDbBean(converterProviders = {DynamoDbJacksonStringifiedConverterProvider.class, DefaultAttributeConverterProvider.class})
public class AISliceInterpretationMetadata extends AIInterpretationMetadata {
	
	protected int sectionStart;
	
	protected int sectionEnd;
	
	protected int sliceIndex;
	
	public static AISliceInterpretationMetadata construct(String provider, String model, int version, BillSlice slice)
	{
		AISliceInterpretationMetadata meta = new AISliceInterpretationMetadata();
		meta.setProvider(provider);
		meta.setModel(model);
		meta.setPromptVersion(version);
		meta.setSectionStart(slice.getSectionStart());
		meta.setSectionEnd(slice.getSectionEnd());
		meta.setSliceIndex(slice.getSliceIndex());
		return meta;
	}
	
}
