package us.poliscore.model.dynamodb;


import java.io.UncheckedIOException;
import java.util.HashSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.AIInterpretationMetadata;
import us.poliscore.model.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.LegislatorBillInteraction;

public class JacksonAttributeConverter <T> implements AttributeConverter<T> {

    private final Class<T> clazz;
    private static final ObjectMapper mapper = PoliscoreUtil.getObjectMapper();

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public JacksonAttributeConverter(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public AttributeValue transformFrom(T input) {
        try {
            return AttributeValue
                    .builder()
                    .s(mapper.writeValueAsString(input))
                    .build();
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Unable to serialize object", e);
        }
    }

    @Override
    public T transformTo(AttributeValue input) {
        try {
        	return mapper.readValue(input.s(), this.clazz);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Unable to parse object", e);
        }
    }

    @Override
    public EnhancedType type() {
        return EnhancedType.of(this.clazz);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
    
    public static class JacksonHashSetConverter extends JacksonAttributeConverter<HashSet> {

        public JacksonHashSetConverter() {
            super(HashSet.class);
        }
    }
    
    public static class LegislatorBillInteractionConverter extends JacksonAttributeConverter<LegislatorBillInteraction> {

        public LegislatorBillInteractionConverter() {
            super(LegislatorBillInteraction.class);
        }
    }
    
    public static class LegislatorBillInteractionSetConverter extends JacksonAttributeConverter<LegislatorBillInteractionSet> {

        public LegislatorBillInteractionSetConverter() {
            super(LegislatorBillInteractionSet.class);
        }
    }
    
    public static class BillInterpretationMetadataConverter extends JacksonAttributeConverter<AIInterpretationMetadata> {
    	
    	public BillInterpretationMetadataConverter() {
    		super(AIInterpretationMetadata.class);
    	}
    }
}
