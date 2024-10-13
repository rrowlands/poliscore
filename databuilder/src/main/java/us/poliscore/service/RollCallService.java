package us.poliscore.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.VoteStatus;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.LegislatorBillInteraction.LegislatorBillVote;
import us.poliscore.service.storage.MemoryObjectService;
import us.poliscore.view.USCRollCallData;
import us.poliscore.view.USCRollCallData.USCRollCallVote;

@ApplicationScoped
public class RollCallService {
	
	public static boolean memorizedRollCall = false;
	
	@Inject
	protected LegislatorService lService;
	
	@Inject
	protected MemoryObjectService memService;
	
	@SneakyThrows
	public void importUscVotes() {
		if (memorizedRollCall) return;
		
		long totalVotes = 0;
		long skipped = 0;
		
		for (File fCongress : Arrays.asList(PoliscoreUtil.USC_DATA.listFiles()).stream()
				.filter(f -> f.getName().matches("\\d+") && f.isDirectory())
				.sorted((a,b) -> a.getName().compareTo(b.getName()))
				.collect(Collectors.toList()))
		{
			if (!PoliscoreUtil.SUPPORTED_CONGRESSES.contains(Integer.valueOf(fCongress.getName()))) continue;
			
			Log.info("Processing " + fCongress.getName() + " congress");
			
			for (File data : PoliscoreUtil.allFilesWhere(new File(fCongress, "votes"), f -> f.getName().equals("data.json")))
			{
				try (var fos = new FileInputStream(data))
				{
					if (importUscJson(fos))
						totalVotes++;
					else
						skipped++;
				}
			}
		}
		
		memorizedRollCall = true;
		
		Log.info("Imported " + totalVotes + " votes. Skipped " + skipped);
	}
	
	@SneakyThrows
	protected boolean importUscJson(InputStream is)
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
		interaction.setLegId(leg.getId());
		interaction.setBillId(bill.getId());
		interaction.setDate(rollCall.getDate().toLocalDate());
		interaction.setBillName(bill.getName());
		
		leg.addBillInteraction(interaction);
		
		memService.put(leg);
	}
	
}
