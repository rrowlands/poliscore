package ch.poliscore.interpretation;

import ch.poliscore.model.DynamoDbJacksonAttributeConverter.DynamoDbJacksonStringifiedConverterProvider;
import ch.poliscore.service.OpenAIService;
import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean(converterProviders = {DynamoDbJacksonStringifiedConverterProvider.class, DefaultAttributeConverterProvider.class})
public class OpenAIInterpretationMetadata extends InterpretationMetadata {
	protected String model;
	
	protected int promptVersion;
	
	public static OpenAIInterpretationMetadata construct()
	{
		OpenAIInterpretationMetadata meta = new OpenAIInterpretationMetadata();
		meta.setModel(OpenAIService.MODEL);
		meta.setPromptVersion(OpenAIService.PROMPT_VERSION);
		return meta;
	}
}
