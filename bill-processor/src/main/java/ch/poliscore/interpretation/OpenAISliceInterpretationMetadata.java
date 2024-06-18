package ch.poliscore.interpretation;

import ch.poliscore.bill.parsing.BillSlice;
import ch.poliscore.model.DynamoDbJacksonAttributeConverter.DynamoDbJacksonStringifiedConverterProvider;
import ch.poliscore.service.OpenAIService;
import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean(converterProviders = {DynamoDbJacksonStringifiedConverterProvider.class, DefaultAttributeConverterProvider.class})
public class OpenAISliceInterpretationMetadata extends OpenAIInterpretationMetadata {
	protected int sectionStart;
	
	protected int sectionEnd;
	
	protected int sliceIndex;
	
	public static OpenAISliceInterpretationMetadata construct(BillSlice slice)
	{
		OpenAISliceInterpretationMetadata meta = new OpenAISliceInterpretationMetadata();
		meta.setModel(OpenAIService.MODEL);
		meta.setPromptVersion(OpenAIService.PROMPT_VERSION);
		meta.setSectionStart(slice.getSectionStart());
		meta.setSectionEnd(slice.getSectionEnd());
		meta.setSliceIndex(slice.getSliceIndex());
		return meta;
	}
}
