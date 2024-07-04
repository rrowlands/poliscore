package us.poliscore.service.storage;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;
import us.poliscore.model.Persistable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedList<T extends Persistable> implements List<T> {
	
	@Delegate
	private List<T> results;
	
	private int pageSize;
	
	private String exclusiveStartKey;
	
	private String lastEvaluatedKey;
	
}
