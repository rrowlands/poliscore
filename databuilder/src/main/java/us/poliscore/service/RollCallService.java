package us.poliscore.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate; // Added import
import java.util.Arrays;
import java.util.List; // Already present
import java.util.Map; // Added import
import java.util.NoSuchElementException;
import java.util.Optional; // Added import
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
// import us.poliscore.view.USCRollCallData; // Commented out as it's related to old logic
// import us.poliscore.view.USCRollCallData.USCRollCallVote; // Commented out
import us.poliscore.view.legiscan.LegiscanVoteView; // Added import

@ApplicationScoped
public class RollCallService {
	
	public static boolean memorizedRollCall = false;
	
	@Inject
	protected LegislatorService lService;
	
	@Inject
	protected MemoryObjectService memService;
	
	@SneakyThrows
	public void importVotes(List<LegiscanVoteView> legiscanVotes) {
		if (legiscanVotes == null || legiscanVotes.isEmpty()) {
			Log.info("No votes to import from Legiscan.");
			return;
		}
		Log.info("Importing " + legiscanVotes.size() + " roll calls from Legiscan for session " + PoliscoreUtil.currentSessionNumber + " in namespace " + PoliscoreUtil.currentNamespace);

		int processedRollCalls = 0;
		int createdInteractions = 0;

		for (LegiscanVoteView voteView : legiscanVotes) {
			Log.info("Processing Roll Call ID: " + voteView.getRoll_call_id() + " for Legiscan Bill ID: " + voteView.getBill_id());

			Optional<Bill> billOpt = memService.queryAll(Bill.class).stream()
				.filter(b -> b.getLegiscanBillId() != null && b.getLegiscanBillId().equals(voteView.getBill_id()) &&
							 b.getNamespace() == PoliscoreUtil.currentNamespace &&
							 b.getSession().equals(PoliscoreUtil.currentSessionNumber))
				.findFirst();

			if (!billOpt.isPresent()) {
				Log.warn("Could not find Bill in memory for Legiscan Bill ID: " + voteView.getBill_id() +
						 " (Namespace: " + PoliscoreUtil.currentNamespace + ", Session: " + PoliscoreUtil.currentSessionNumber + ")");
				continue;
			}
			Bill foundBill = billOpt.get();

			if (voteView.getVotes() == null || voteView.getVotes().isEmpty()) {
				Log.info("No individual voter records for Roll Call ID: " + voteView.getRoll_call_id());
				processedRollCalls++;
				continue;
			}
			
			LocalDate voteDate;
			try {
				voteDate = LocalDate.parse(voteView.getDate()); // Assuming YYYY-MM-DD
			} catch (Exception e) {
				Log.warn("Could not parse date for Roll Call ID: " + voteView.getRoll_call_id() + ", Date: " + voteView.getDate(), e);
				continue; // Skip this roll call if date is invalid
			}

			for (Map.Entry<String, String> entry : voteView.getVotes().entrySet()) {
				String legiscanLegId = entry.getKey();
				String voteString = entry.getValue();

				Optional<Legislator> legOpt = memService.queryAll(Legislator.class).stream()
					.filter(l -> l.getLegiscanId() != null && l.getLegiscanId().equals(legiscanLegId) &&
								 l.getSession().equals(PoliscoreUtil.currentSessionNumber) &&
								 l.getStorageBucket().contains(PoliscoreUtil.currentNamespace.getNamespace())) // Check namespace via storage bucket
					.findFirst();

				if (!legOpt.isPresent()) {
					Log.warn("Could not find Legislator in memory for Legiscan Legislator ID: " + legiscanLegId +
							 " (Namespace: " + PoliscoreUtil.currentNamespace + ", Session: " + PoliscoreUtil.currentSessionNumber + ")");
					continue;
				}
				Legislator foundLegislator = legOpt.get();

				VoteStatus mappedVoteStatus = mapLegiscanVote(voteString);
				if (mappedVoteStatus == VoteStatus.OTHER && !voteString.equalsIgnoreCase("Other")) { // Log if it wasn't explicitly "Other"
                    Log.warn("Unknown vote string '" + voteString + "' for Legislator ID: " + legiscanLegId + " on Roll Call ID: " + voteView.getRoll_call_id() + ". Defaulted to OTHER.");
                }


				LegislatorBillVote interaction = new LegislatorBillVote();
				interaction.setLegId(foundLegislator.getId());
				interaction.setBillId(foundBill.getId());
				interaction.setBillName(foundBill.getShortName());
				interaction.setDate(voteDate);
				interaction.setVoteStatus(mappedVoteStatus);

				// Populate interaction details from the bill
				// Worker note: Add a null check for foundBill.getInterpretation()
				if (foundBill.getInterpretation() != null) {
					interaction.populate(foundBill, foundBill.getInterpretation());
				} else {
					Log.debug("Bill " + foundBill.getId() + " does not have an interpretation yet. Skipping populate() for interaction.");
					// Minimal population if no interpretation
                    interaction.setIssueStats(new us.poliscore.model.IssueStats()); // Empty stats
                    interaction.setShortExplain("Vote on " + foundBill.getShortName()); // Basic explanation
                    interaction.setStatusProgress(foundBill.getStatus() != null ? foundBill.getStatus().getProgress() : 0f);
                    interaction.setCosponsorPercent(foundBill.getCosponsorPercent());
				}


				foundLegislator.addBillInteraction(interaction);
				memService.put(foundLegislator);
				createdInteractions++;
			}
			processedRollCalls++;
		}
		Log.info("Finished importing votes. Processed " + processedRollCalls + " roll calls and created " + createdInteractions + " legislator bill interactions.");
		memorizedRollCall = true; // Set flag after processing
	}

	private VoteStatus mapLegiscanVote(String voteString) {
		if (voteString == null) return VoteStatus.OTHER;
		String voteLower = voteString.toLowerCase();
		switch (voteLower) {
			case "yea":
			case "aye":
			case "yes":
				return VoteStatus.AYE;
			case "nay":
			case "no":
				return VoteStatus.NAY;
			case "not voting":
			case "absent":
			case "excused": // Common in Legiscan
				return VoteStatus.NOT_VOTING;
			case "present":
				return VoteStatus.PRESENT;
			default:
				return VoteStatus.OTHER;
		}
	}
	
	@SneakyThrows
	// protected boolean importUscJson(InputStream is) // Old method
	protected boolean importSingleVote_Old(InputStream is) // Renamed
	{
		// USCRollCallData rollCall = PoliscoreUtil.getObjectMapper().readValue(is, USCRollCallData.class);
		
		// There are a lot of roll call categories that we don't care about. Quorum is one of them.
		// if (!rollCall.getCategory().contains("passage")) return false;
//		if (rollCall.getBill() == null) return false;
		
		// There are some bill types we don't care about. Don't bother printing noisy warnings or anything
		// if (BillType.getIgnoredBillTypes().contains(BillType.valueOf(rollCall.getBill().getType().toUpperCase()))) return false;
		
		// rollCall.getVotes().getAffirmative().forEach(v -> process(rollCall, v, VoteStatus.AYE));
		// rollCall.getVotes().getNegative().forEach(v -> process(rollCall, v, VoteStatus.NAY));
		
		// At the moment these are just pointless noise so we're ignoring them.
//		rollCall.getVotes().getNotVoting().forEach(v -> process(rollCall, v, VoteStatus.NOT_VOTING));
//		rollCall.getVotes().getPresent().forEach(v -> process(rollCall, v, VoteStatus.PRESENT));
		
		return true; // Placeholder
	}
	
	// protected void process(USCRollCallData rollCall, USCRollCallVote vote, VoteStatus vs) // Old method, commented out
	// {
		// Legislator leg;
		// try
		// {
			// if (vote.getId().length() == 4 && vote.getId().startsWith("S"))
				// leg = memService.query(Legislator.class).stream().filter(l -> vote.getId().equals(l.getLisId())).findFirst().orElseThrow();
			// else
				// leg = memService.get(Legislator.generateId(LegislativeNamespace.US_CONGRESS, PoliscoreUtil.currentSessionNumber, vote.getId()), Legislator.class).orElseThrow(); // Updated
		// }
		// catch (NoSuchElementException ex)
		// {
			// Log.warn("Could not find legislator with bioguide id " + vote.getId());
			// return;
		// }
		
		// Bill bill;
		// var billView = rollCall.getBill();
		// var billId = Bill.generateId(billView.getCongress(), BillType.valueOf(billView.getType().toUpperCase()), billView.getNumber());
		// try
		// {
			// bill = memService.get(billId, Bill.class).orElseThrow();
		// }
		// catch (NoSuchElementException ex)
		// {
			// Log.warn("Could not find bill with id " + billId);
			// return;
		// }
		
		// LegislatorBillVote interaction = new LegislatorBillVote(vs);
		// interaction.setLegId(leg.getId());
		// interaction.setBillId(bill.getId());
		// interaction.setDate(rollCall.getDate().toLocalDate());
		// interaction.setBillName(bill.getName());
		
		// leg.addBillInteraction(interaction);
		
		// memService.put(leg);
	// }
	
}
