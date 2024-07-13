package us.poliscore;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import us.poliscore.model.Legislator;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LegislatorPageData {
	private List<Legislator> legislators;
	
	private List<List<String>> allLegislators;
}
