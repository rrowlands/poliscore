package us.poliscore.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = AISliceInterpretationMetadata.class, name = "AISliceInterpretationMetadata"),
    @JsonSubTypes.Type(value = AIInterpretationMetadata.class, name = "AIInterpretationMetadata") }
)
abstract public class InterpretationMetadata {
	
}
