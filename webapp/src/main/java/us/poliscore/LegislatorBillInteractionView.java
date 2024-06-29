package us.poliscore;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import us.poliscore.model.IssueStats;
import us.poliscore.model.LegislatorBillInteraction;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LegislatorBillInteractionView {
	
	@NonNull
	protected String interactionName;
	
	@NonNull
	protected String billId;
	
	@NonNull
	protected String billName;
	
	@EqualsAndHashCode.Exclude
	protected IssueStats issueStats;
	
	@NonNull
	@EqualsAndHashCode.Exclude
	protected LocalDate date;
	
	public static LegislatorBillInteractionView toView(LegislatorBillInteraction interact)
	{
		return new LegislatorBillInteractionView(interact.getClass().getSimpleName(), interact.getBillId(), interact.getBillName(), interact.getIssueStats(), interact.getDate());
	}
	
}
