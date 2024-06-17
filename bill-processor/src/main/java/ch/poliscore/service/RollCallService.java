package ch.poliscore.service;

import java.io.InputStream;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.VoteStatus;
import ch.poliscore.interpretation.BillType;
import ch.poliscore.model.Bill;
import ch.poliscore.model.Legislator;
import ch.poliscore.model.LegislatorBillInteration.LegislatorBillVote;
import ch.poliscore.view.USCRollCallData;
import ch.poliscore.view.USCRollCallData.USCRollCallVote;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;

@ApplicationScoped
public class RollCallService {
	
	@Inject
	protected LegislatorService lService;
	
	@SneakyThrows
	public void importUscData(InputStream is)
	{
		USCRollCallData rollCall = new ObjectMapper().readValue(is, USCRollCallData.class);
		
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
			Legislator leg = lService.getById(vote.getId()).orElseThrow();
			
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
