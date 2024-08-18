package us.poliscore;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import us.poliscore.model.legislator.Legislator;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class LegislatorPageData {
	private String location;
	
	private List<Legislator> legislators;
	
	private List<List<String>> allLegislators;
}
