package us.poliscore.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import us.poliscore.model.DynamoDbJacksonAttributeConverter.DynamoDbJacksonStringifiedConverterProvider;

@Data
@DynamoDbBean(converterProviders = {DynamoDbJacksonStringifiedConverterProvider.class, DefaultAttributeConverterProvider.class})
public class AIInterpretationMetadata extends InterpretationMetadata {
	
	protected String provider;
	
	protected String model;
	
	protected int promptVersion;
	
	public static AIInterpretationMetadata construct(String provider, String model, int promptVersion)
	{
		AIInterpretationMetadata meta = new AIInterpretationMetadata();
		meta.setProvider(provider);
		meta.setModel(model);
		meta.setPromptVersion(promptVersion);
		return meta;
	}
	
}
