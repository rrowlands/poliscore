package us.poliscore;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import us.poliscore.model.Persistable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class LegislatorPageData {
	private String location;
	
	private List<Persistable> legislators;
	
	private List<List<String>> allLegislators;
}
