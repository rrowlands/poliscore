package ch.poliscore.legislator;

import java.util.Date;

import ch.poliscore.IssueStats;
import ch.poliscore.VoteStatus;
import lombok.Data;

@Data
public class VotingData {
	
	protected String billId;
	
	protected VoteStatus voteStatus;
	
	protected IssueStats issueStats;
	
	protected Date date;
	
}
