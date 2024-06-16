package ch.poliscore.interpretation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = OpenAISliceInterpretationMetadata.class, name = "OpenAISliceInterpretationMetadata"),
    @JsonSubTypes.Type(value = OpenAIInterpretationMetadata.class, name = "OpenAIInterpretationMetadata") }
)
abstract public class InterpretationMetadata {
}
