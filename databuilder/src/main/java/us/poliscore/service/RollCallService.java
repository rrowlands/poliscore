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
import us.poliscore.model.LegislatorBillInteraction.LegislatorBillVote;
import us.poliscore.model.VoteStatus;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillType;
import us.poliscore.service.storage.MemoryPersistenceService;
import us.poliscore.view.USCRollCallData;
import us.poliscore.view.USCRollCallData.USCRollCallVote;

@ApplicationScoped
public class RollCallService {
	
	@Inject
	protected LegislatorService lService;
	
	@Inject
	protected MemoryPersistenceService memService;
	
	@SneakyThrows
	public boolean importUscData(InputStream is)
	{
		USCRollCallData rollCall = PoliscoreUtil.getObjectMapper().readValue(is, USCRollCallData.class);
		
		// There are a lot of roll call categories that we don't care about. Quorum is one of them.
		if (!rollCall.getCategory().contains("passage")) return false;
//		if (rollCall.getBill() == null) return false;
		
		// There are some bill types we don't care about. Don't bother printing noisy warnings or anything
		if (BillType.getIgnoredBillTypes().contains(BillType.valueOf(rollCall.getBill().getType().toUpperCase()))) return false;
		
		rollCall.getVotes().getAye().forEach(v -> process(rollCall, v, VoteStatus.AYE));
		rollCall.getVotes().getNo().forEach(v -> process(rollCall, v, VoteStatus.NAY));
		
		// At the moment these are just pointless noise so we're ignoring them.
//		rollCall.getVotes().getNotVoting().forEach(v -> process(rollCall, v, VoteStatus.NOT_VOTING));
//		rollCall.getVotes().getPresent().forEach(v -> process(rollCall, v, VoteStatus.PRESENT));
		
		return true;
	}
	
	protected void process(USCRollCallData rollCall, USCRollCallVote vote, VoteStatus vs)
	{
		Legislator leg;
		try
		{
			leg = lService.getById(Legislator.generateId(LegislativeNamespace.US_CONGRESS, vote.getId())).orElseThrow();
		}
		catch (NoSuchElementException ex)
		{
			Log.warn("Could not find legislator with bioguide id " + vote.getId());
			return;
		}
		
		Bill bill;
		var billView = rollCall.getBill();
		var billId = Bill.generateId(billView.getCongress(), BillType.valueOf(billView.getType().toUpperCase()), billView.getNumber());
		try
		{
			bill = memService.get(billId, Bill.class).orElseThrow();
		}
		catch (NoSuchElementException ex)
		{
			Log.warn("Could not find bill with id " + billId);
			return;
		}
		
		LegislatorBillVote interaction = new LegislatorBillVote(vs);
		interaction.setBillId(bill.getId());
		interaction.setDate(rollCall.getDate().toLocalDate());
		interaction.setBillName(bill.getName());
		
		leg.addBillInteraction(interaction);
		
		memService.put(leg);
	}
	
}
