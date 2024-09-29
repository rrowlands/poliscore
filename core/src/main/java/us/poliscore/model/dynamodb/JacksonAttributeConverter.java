package us.poliscore.model.dynamodb;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.utils.ImmutableMap;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.AIInterpretationMetadata;
import us.poliscore.model.legislator.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.legislator.Legislator.LegislatorLegislativeTermSortedSet;
import us.poliscore.model.session.SessionInterpretation.PartyInterpretation;
import us.poliscore.model.legislator.LegislatorBillInteraction;

public class JacksonAttributeConverter <T> implements AttributeConverter<T> {

    protected final Class<T> clazz;
    protected static final ObjectMapper mapper = PoliscoreUtil.getObjectMapper();

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
    
    public static class CompressedJacksonAttributeConverter <T> extends JacksonAttributeConverter<T> {

		public CompressedJacksonAttributeConverter(Class<T> clazz) {
			super(clazz);
		}
		
		@Override
		@SneakyThrows
	    public AttributeValue transformFrom(T input) {
			@Cleanup val baos = new ByteArrayOutputStream();
			@Cleanup val zos = new GZIPOutputStream(baos);
			zos.write(mapper.writeValueAsString(input).getBytes());
			zos.close();
			
            return AttributeValue
                    .builder()
                    .b(SdkBytes.fromByteArray(baos.toByteArray()))
                    .build();
	    }

	    @Override
	    @SneakyThrows
	    public T transformTo(AttributeValue input) {
	    	try {
		    	@Cleanup val bais = new GZIPInputStream(new ByteArrayInputStream(input.b().asByteArray()));
		    	
	        	return mapper.readValue(bais.readAllBytes(), this.clazz);
	    	}
	    	catch (Exception e) {
	    		Log.error(e);
	    		return this.clazz.newInstance();
	    	}
	    }
	    
	    @Override
	    public AttributeValueType attributeValueType() {
	        return AttributeValueType.B;
	    }
    	
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
    
    public static class AIInterpretationMetadataConverter extends JacksonAttributeConverter<AIInterpretationMetadata> {
    	
    	public AIInterpretationMetadataConverter() {
    		super(AIInterpretationMetadata.class);
    	}
    }
    
    public static class LegislatorLegislativeTermSortedSetConverter extends JacksonAttributeConverter<LegislatorLegislativeTermSortedSet> {
    	
    	public LegislatorLegislativeTermSortedSetConverter() {
    		super(LegislatorLegislativeTermSortedSet.class);
    	}
    }
    
//    public static class CompressedLegislatorLegislativeTermSortedSetConverter extends CompressedJacksonAttributeConverter<LegislatorLegislativeTermSortedSet> {
//    	
//    	public CompressedLegislatorLegislativeTermSortedSetConverter() {
//    		super(LegislatorLegislativeTermSortedSet.class);
//    	}
//    }
    
    public static class CompressedLegislatorBillInteractionSetConverter extends CompressedJacksonAttributeConverter<LegislatorBillInteractionSet> {
    	
    	public CompressedLegislatorBillInteractionSetConverter() {
    		super(LegislatorBillInteractionSet.class);
    	}
    }
    
    public static class CompressedPartyStatsConverter extends CompressedJacksonAttributeConverter<PartyInterpretation> {
    	
    	public CompressedPartyStatsConverter() {
    		super(PartyInterpretation.class);
    	}
    }
    
    public static final class LegislatorBillInteractionSetConverterProvider implements AttributeConverterProvider {
        private final Map<EnhancedType<?>, AttributeConverter<?>> converterCache = ImmutableMap.of(
                // 1. Add HttpCookieConverter to the internal cache.
                EnhancedType.of(LegislatorBillInteractionSet.class), new LegislatorBillInteractionSetConverter());

        public static LegislatorBillInteractionSetConverterProvider create() {
            return new LegislatorBillInteractionSetConverterProvider();
        }

        // The SDK calls this method to find out if the provider contains a AttributeConverter instance
        // for the EnhancedType<T> argument.
        @SuppressWarnings("unchecked")
        @Override
        public <T> AttributeConverter<T> converterFor(EnhancedType<T> enhancedType) {
            return (AttributeConverter<T>) converterCache.get(enhancedType);
        }
    }
}
