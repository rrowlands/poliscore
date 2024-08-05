package us.poliscore;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import us.poliscore.model.Legislator;
import us.poliscore.model.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.Legislator.LegislatorLegislativeTermSortedSet;
import us.poliscore.model.Legislator.LegislatorName;
import us.poliscore.model.LegislatorBillInteraction;
import us.poliscore.model.LegislatorInterpretation;
import us.poliscore.model.TrackedIssue;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LegislatorView {
	
	@NonNull
	protected LegislatorName name;
	
	protected String bioguideId;
	
	protected String id;
	
	protected LocalDate birthday;
	
	protected LegislatorLegislativeTermSortedSet terms;
	
	protected LegislatorInterpretation interpretation;
	
	protected LegislatorBillInteractionSet interactions = new LegislatorBillInteractionSet();
	
	protected Map<TrackedIssue, List<LegislatorBillInteraction>> topInteractions = new HashMap<TrackedIssue, List<LegislatorBillInteraction>>();
	
	public static LegislatorView toView(Legislator leg)
	{
		return new LegislatorView(
				leg.getName(),
				leg.getBioguideId(),
				leg.getId(),
				leg.getBirthday(),
				leg.getTerms(),
				leg.getInterpretation(),
				leg.getInteractions(),
				leg.calculateTopInteractions()
//				leg.getInteractions().stream().map(i -> LegislatorBillInteractionView.toView(i)).collect(Collectors.toCollection(TreeSet::new))
		);
	}

}
