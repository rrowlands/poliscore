package us.poliscore.interpretation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import us.poliscore.model.DynamoDbJacksonAttributeConverter.DynamoDbJacksonStringifiedConverterProvider;
import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = OpenAISliceInterpretationMetadata.class, name = "OpenAISliceInterpretationMetadata"),
    @JsonSubTypes.Type(value = OpenAIInterpretationMetadata.class, name = "OpenAIInterpretationMetadata") }
)
@DynamoDbBean(converterProviders = {DynamoDbJacksonStringifiedConverterProvider.class, DefaultAttributeConverterProvider.class})
abstract public class InterpretationMetadata {
	
}
