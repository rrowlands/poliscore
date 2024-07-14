package us.poliscore.model;

import java.util.concurrent.TimeUnit;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Exclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

@Data
@RegisterForReflection
@DynamoDbBean
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class IpLocationMapping implements Persistable {
	
	public static final String ID_CLASS_PREFIX = "IP";
	
	@Getter(onMethod = @__({ @DynamoDbPartitionKey}))
	private String id; // the ip
	
	@Exclude
	private String location;
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX, Persistable.OBJECT_BY_LOCATION_INDEX }) public String getIdClassPrefix() { return ID_CLASS_PREFIX; }
	@Override @JsonIgnore public void setIdClassPrefix(String prefix) { }
	
	public long getExpireDate() { return TimeUnit.MILLISECONDS.toSeconds(LocalDate.now().plusMonths(6).toDateTimeAtStartOfDay(DateTimeZone.UTC).toInstant().getMillis()); }
	public void setExpireDate(long expire) { }
	
}
