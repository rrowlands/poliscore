package us.poliscore.service;

import java.io.InputStream;
import java.util.NoSuchElementException;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Legislator;
import us.poliscore.model.LegislatorBillInteration.LegislatorBillVote;
import us.poliscore.model.VoteStatus;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillType;
import us.poliscore.view.USCRollCallData;
import us.poliscore.view.USCRollCallData.USCRollCallVote;

@ApplicationScoped
public class RollCallService {
	
	@Inject
	protected LegislatorService lService;
	
	@SneakyThrows
	public void importUscData(InputStream is)
	{
		USCRollCallData rollCall = PoliscoreUtil.getObjectMapper().readValue(is, USCRollCallData.class);
		
		if (!"passage".equals(rollCall.getCategory()) || rollCall.getBill() == null) return;
		
		rollCall.getVotes().getAye().forEach(v -> process(rollCall, v, VoteStatus.AYE));
		rollCall.getVotes().getNo().forEach(v -> process(rollCall, v, VoteStatus.NAY));
		rollCall.getVotes().getNotVoting().forEach(v -> process(rollCall, v, VoteStatus.NOT_VOTING));
		rollCall.getVotes().getPresent().forEach(v -> process(rollCall, v, VoteStatus.PRESENT));
	}
	
	protected void process(USCRollCallData rollCall, USCRollCallVote vote, VoteStatus vs)
	{
		try
		{
			Legislator leg = lService.getById(Legislator.generateId(LegislativeNamespace.US_CONGRESS, vote.getId())).orElseThrow();
			
			var billView = rollCall.getBill();
			var billId = Bill.generateId(billView.getCongress(), BillType.valueOf(billView.getType().toUpperCase()), billView.getNumber());
			
			LegislatorBillVote interaction = new LegislatorBillVote(vs);
			interaction.setBillId(billId);
			interaction.setDate(rollCall.getDate());
			
			leg.addBillInteraction(interaction);
			
			lService.persist(leg);
		}
		catch (NoSuchElementException ex)
		{
			Log.warn("Could not find legislator with id [" + vote.getId() + "] referenced in file ?");
		}
	}
	
}
