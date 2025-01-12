package us.poliscore.model.bill;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;


/*
 * Goals:
 * 1. Display bill status as a text on the bill page (similar to congress's "bill tracker" feature)
 * 2. Sort bills by "importance" which ranks bills higher if they're further along in the process
 * 3. Highlight laws in the bills list and rank them super high
 * 4. Sort by importance when interpreting legislators and tell AI what the progress is and if its a law
 * 5. Should work with state legislatures as well (not all have house/senate)
 */
@Data
@DynamoDbBean
@RequiredArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class BillStatus {
	
	@NonNull
	private String description;
	
	@NonNull
	private float progress;
	
	@NonNull
	private String sourceStatus;
	
}
