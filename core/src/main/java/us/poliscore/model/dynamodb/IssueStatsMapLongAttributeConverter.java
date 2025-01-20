package us.poliscore.model.dynamodb;

import java.util.Map;
import java.util.stream.Collectors;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import us.poliscore.model.TrackedIssue;

public class IssueStatsMapLongAttributeConverter implements AttributeConverter<Map<TrackedIssue, Long>> {

  @Override
  public AttributeValue transformFrom(final Map<TrackedIssue, Long> input) {
    Map<String, AttributeValue> attributeValueMap = input.entrySet().stream()
            .collect(
                Collectors.toMap(
                    k -> k.getKey().name(),
                    v -> AttributeValue.builder().n(String.valueOf(v.getValue())).build()));
    return AttributeValue.builder().m(attributeValueMap).build();
  }

  @Override
  public Map<TrackedIssue, Long> transformTo(final AttributeValue input) {
    return input.m().entrySet().stream()
        .collect(
            Collectors.toMap(
                k -> getEnumClassKeyByString(k.getKey()), v -> Long.parseLong(v.getValue().n())));
  }

  private TrackedIssue getEnumClassKeyByString(final String key) {
    TrackedIssue enumClass = TrackedIssue.valueOf(key);
    return enumClass != null ? enumClass : TrackedIssue.AgricultureAndFood;
  }

  @Override
  public EnhancedType<Map<TrackedIssue, Long>> type() {
    return EnhancedType.mapOf(TrackedIssue.class, Long.class);
  }

  @Override
  public AttributeValueType attributeValueType() {
    return AttributeValueType.M;
  }
}