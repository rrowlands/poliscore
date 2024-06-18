package ch.poliscore.model;

import java.util.Map;

import ch.poliscore.PoliscoreUtil;
import ch.poliscore.model.DynamoDbJacksonAttributeConverter.DynamoDbJacksonStringified;
import lombok.SneakyThrows;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.utils.ImmutableMap;

public class DynamoDbJacksonAttributeConverter implements AttributeConverter<DynamoDbJacksonStringified> {

	@Override
	public AttributeValue transformFrom(DynamoDbJacksonStringified input) {
		return AttributeValue.fromS(PoliscoreUtil.getObjectMapper().valueToTree(input).toString());
	}

	@Override
	@SneakyThrows
	public DynamoDbJacksonStringified transformTo(AttributeValue input) {
		return PoliscoreUtil.getObjectMapper().readValue(input.s(), DynamoDbJacksonStringified.class);
	}

	@Override
	public EnhancedType<DynamoDbJacksonStringified> type() {
		return EnhancedType.of(DynamoDbJacksonStringified.class);
	}

	@Override
	public AttributeValueType attributeValueType() {
		return AttributeValueType.S;
	}
	
	public static final class DynamoDbJacksonStringifiedConverterProvider implements AttributeConverterProvider {
        private final Map<EnhancedType<?>, AttributeConverter<?>> converterCache = ImmutableMap.of(
                // 1. Add DynamoDbJacksonStringifiedConverter to the internal cache.
                EnhancedType.of(DynamoDbJacksonStringified.class), new DynamoDbJacksonAttributeConverter());

        public static DynamoDbJacksonStringifiedConverterProvider create() {
            return new DynamoDbJacksonStringifiedConverterProvider();
        }

        // The SDK calls this method to find out if the provider contains a AttributeConverter instance
        // for the EnhancedType<T> argument.
        @SuppressWarnings("unchecked")
        @Override
        public <T> AttributeConverter<T> converterFor(EnhancedType<T> enhancedType) {
            return (AttributeConverter<T>) converterCache.get(enhancedType);
        }
    }
	
	public static interface DynamoDbJacksonStringified {}
}
