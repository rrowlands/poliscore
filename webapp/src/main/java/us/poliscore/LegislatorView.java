package us.poliscore;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.LegislatorBillInteraction;
import us.poliscore.model.legislator.LegislatorInterpretation;
import us.poliscore.model.legislator.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.legislator.Legislator.LegislatorLegislativeTermSortedSet;
import us.poliscore.model.legislator.Legislator.LegislatorName;

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
	
//	protected Map<TrackedIssue, List<LegislatorBillInteraction>> topInteractions = new HashMap<TrackedIssue, List<LegislatorBillInteraction>>();
	
	public static LegislatorView toView(Legislator leg)
	{
		return new LegislatorView(
				leg.getName(),
				leg.getBioguideId(),
				leg.getId(),
				leg.getBirthday(),
				leg.getTerms(),
				leg.getInterpretation(),
				leg.getInteractions()
//				leg.getInteractions().stream().map(i -> LegislatorBillInteractionView.toView(i)).collect(Collectors.toCollection(TreeSet::new))
		);
	}

}
