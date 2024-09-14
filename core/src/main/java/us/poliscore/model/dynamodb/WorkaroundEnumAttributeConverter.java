package us.poliscore.model.dynamodb;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.EnumAttributeConverter;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.utils.ImmutableMap;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.VoteStatus;

public class WorkaroundEnumAttributeConverter<T extends Enum<T>> implements AttributeConverter<T> {
    private final EnumAttributeConverter<T> converter;

    public WorkaroundEnumAttributeConverter(final Class<T> enumClass) {
        this.converter = EnumAttributeConverter.create(enumClass);
    }

    @Override
    public AttributeValue transformFrom(T t) {
        return this.converter.transformFrom(t);
    }

    @Override
    public T transformTo(AttributeValue attributeValue) {
        return this.converter.transformTo(attributeValue);
    }

    @Override
    public EnhancedType<T> type() {
        return this.converter.type();
    }

    @Override
    public AttributeValueType attributeValueType() {
        return this.converter.attributeValueType();
    }
    
    public static class VoteStatusAttributeConverter extends WorkaroundEnumAttributeConverter<VoteStatus> {
        public VoteStatusAttributeConverter() {
            super(VoteStatus.class);
        }
    }
    
    public static final class VoteStatusAttributeConverterProvider implements AttributeConverterProvider {
        private final Map<EnhancedType<?>, AttributeConverter<?>> converterCache = ImmutableMap.of(
                // 1. Add DynamoDbJacksonStringifiedConverter to the internal cache.
                EnhancedType.of(VoteStatus.class), new VoteStatusAttributeConverter());

        public static VoteStatusAttributeConverterProvider create() {
            return new VoteStatusAttributeConverterProvider();
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