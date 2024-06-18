package ch.poliscore.model;

import java.util.Map;
import java.util.stream.Collectors;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class EnumMapAttributeConverter implements AttributeConverter<Map<TrackedIssue, String>> {

  @Override
  public AttributeValue transformFrom(final Map<TrackedIssue, String> input) {
    Map<String, AttributeValue> attributeValueMap = input.entrySet().stream()
            .collect(
                Collectors.toMap(
                    k -> k.getKey().getName(),
                    v -> AttributeValue.builder().s(v.getValue()).build()));
    return AttributeValue.builder().m(attributeValueMap).build();
  }

  @Override
  public Map<TrackedIssue, String> transformTo(final AttributeValue input) {
    return input.m().entrySet().stream()
        .collect(
            Collectors.toMap(
                k -> getEnumClassKeyByString(k.getKey()), v -> v.getValue().s()));
  }

  private TrackedIssue getEnumClassKeyByString(final String key) {
    TrackedIssue enumClass = TrackedIssue.valueOf(key);
    return enumClass != null ? enumClass : TrackedIssue.AgricultureAndFood;
  }

  @Override
  public EnhancedType<Map<TrackedIssue, String>> type() {
    return EnhancedType.mapOf(TrackedIssue.class, String.class);
  }

  @Override
  public AttributeValueType attributeValueType() {
    return AttributeValueType.M;
  }
}