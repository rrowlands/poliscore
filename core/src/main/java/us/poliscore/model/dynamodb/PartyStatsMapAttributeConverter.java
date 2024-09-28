package us.poliscore.model.dynamodb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.Party;
import us.poliscore.model.session.SessionInterpretation.PartyInterpretation;

public class PartyStatsMapAttributeConverter implements AttributeConverter<Map<Party, PartyInterpretation>> {
  protected static final ObjectMapper mapper = PoliscoreUtil.getObjectMapper();

  @Override
  @SneakyThrows
  public AttributeValue transformFrom(final Map<Party, PartyInterpretation> input) {
//    Map<String, AttributeValue> attributeValueMap = input.entrySet().stream()
//            .collect(
//                Collectors.toMap(
//                    k -> k.getKey().name(),
//                    v -> {
//						try {
//							return AttributeValue.builder().s(mapper.writeValueAsString(v.getValue())).build();
//						} catch (JsonProcessingException e) {
//							throw new RuntimeException(e);
//						}
//					}));
    
//	  return AttributeValue.builder().m(attributeValueMap).build();
	  
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
  public Map<Party, PartyInterpretation> transformTo(final AttributeValue input) {
//    return input.m().entrySet().stream()
//        .collect(
//            Collectors.toMap(
//                k -> getEnumClassKeyByString(k.getKey()), v -> {
//					try {
//						return mapper.readValue(v.getValue().s(), PartyStats.class);
//					} catch (JsonProcessingException e) {
//						throw new RuntimeException(e);
//					}
//				}));
	  
	@Cleanup val bais = new GZIPInputStream(new ByteArrayInputStream(input.b().asByteArray()));
    	
  	return mapper.readValue(bais.readAllBytes(), Map.class);
  }

//  private Party getEnumClassKeyByString(final String key) {
//    Party enumClass = Party.valueOf(key);
//    return enumClass != null ? enumClass : Party.INDEPENDENT;
//  }

  @Override
  public EnhancedType<Map<Party, PartyInterpretation>> type() {
    return EnhancedType.mapOf(Party.class, PartyInterpretation.class);
  }

  @Override
  public AttributeValueType attributeValueType() {
    return AttributeValueType.B;
  }
}