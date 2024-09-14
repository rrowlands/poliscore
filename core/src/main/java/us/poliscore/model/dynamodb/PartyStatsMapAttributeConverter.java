package us.poliscore.model.dynamodb;

import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.Party;
import us.poliscore.model.stats.SessionStats.PartyStats;

public class PartyStatsMapAttributeConverter implements AttributeConverter<Map<Party, PartyStats>> {
  protected static final ObjectMapper mapper = PoliscoreUtil.getObjectMapper();

  @Override
  public AttributeValue transformFrom(final Map<Party, PartyStats> input) {
    Map<String, AttributeValue> attributeValueMap = input.entrySet().stream()
            .collect(
                Collectors.toMap(
                    k -> k.getKey().name(),
                    v -> {
						try {
							return AttributeValue.builder().s(mapper.writeValueAsString(v.getValue())).build();
						} catch (JsonProcessingException e) {
							throw new RuntimeException(e);
						}
					}));
    return AttributeValue.builder().m(attributeValueMap).build();
  }

  @Override
  public Map<Party, PartyStats> transformTo(final AttributeValue input) {
    return input.m().entrySet().stream()
        .collect(
            Collectors.toMap(
                k -> getEnumClassKeyByString(k.getKey()), v -> {
					try {
						return mapper.readValue(v.getValue().s(), PartyStats.class);
					} catch (JsonProcessingException e) {
						throw new RuntimeException(e);
					}
				}));
  }

  private Party getEnumClassKeyByString(final String key) {
    Party enumClass = Party.valueOf(key);
    return enumClass != null ? enumClass : Party.INDEPENDENT;
  }

  @Override
  public EnhancedType<Map<Party, PartyStats>> type() {
    return EnhancedType.mapOf(Party.class, PartyStats.class);
  }

  @Override
  public AttributeValueType attributeValueType() {
    return AttributeValueType.M;
  }
}