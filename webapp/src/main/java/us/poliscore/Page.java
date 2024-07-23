package us.poliscore;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import us.poliscore.model.LegislatorBillInteraction;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class Page<T> {
	private List<T> data;
	
	private Object exclusiveStartKey;
	
	private boolean hasMoreData;
}
