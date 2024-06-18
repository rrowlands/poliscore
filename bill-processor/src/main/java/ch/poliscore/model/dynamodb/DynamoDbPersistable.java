package ch.poliscore.model.dynamodb;

import ch.poliscore.model.Persistable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

public abstract class DynamoDbPersistable implements Persistable {
	
	abstract public String getId();
	
	abstract public void setId(String id);
	
	@DynamoDbPartitionKey
	public String getDdbId() { return this.getClass().getSimpleName() + "/" + getId(); }
	
	public void setDdbId(String id) { setId(id.replaceFirst(this.getClass().getSimpleName() + "/", "")); }
	
}
