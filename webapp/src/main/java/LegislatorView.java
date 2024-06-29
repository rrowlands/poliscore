import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import us.poliscore.model.Legislator;
import us.poliscore.model.Legislator.LegislatorName;
import us.poliscore.model.LegislatorInterpretation;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LegislatorView {
	
	@NonNull
	protected LegislatorName name;
	
	protected String id;
	
	protected LegislatorInterpretation interpretation;
	
	protected Set<LegislatorBillInteractionView> interactions = new HashSet<LegislatorBillInteractionView>();
	
	public static LegislatorView toView(Legislator leg)
	{
		return new LegislatorView(
				leg.getName(),
				leg.getId(),
				leg.getInterpretation(),
				leg.getInteractions().stream().map(i -> LegislatorBillInteractionView.toView(i)).collect(Collectors.toSet())
		);
	}

}
