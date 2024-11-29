package us.poliscore;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.Legislator.LegislatorBillInteractionList;
import us.poliscore.model.legislator.Legislator.LegislatorLegislativeTermSortedSet;
import us.poliscore.model.legislator.Legislator.LegislatorName;
import us.poliscore.model.legislator.LegislatorInterpretation;

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
	
	protected LegislatorBillInteractionList interactions = new LegislatorBillInteractionList();
	
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
