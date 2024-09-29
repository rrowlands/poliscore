package us.poliscore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.IssueStats;
import us.poliscore.model.Party;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;

@ApplicationScoped
public class PartyInterpretationService {
	public static final String PROMPT_TEMPLATE = """
			You are part of a U.S. non-partisan oversight committee which has graded the {{partyName}} party for the {{session}} congressional session. The party has received the following policy area grades (scores range from -100 to 100):
			
			{{stats}}
			
			Based on these scores, this party has received the overall letter grade: {{letterGrade}}. You will be given bill interaction summaries of this party's recent legislative history, sorted by their impact to the relevant policy area grades. Please generate a layman's, concise, three paragraph, {{analysisType}}, highlighting any {{behavior}}, identifying trends, referencing specific bill titles (in quotes), and pointing out major focuses and priorities of the party. Focus on the policy areas with the largest score magnitudes (either positive or negative). Do not include the party's policy area grade scores and do not mention their letter grade in your summary.
			""";
	
	@Inject
	private MemoryPersistenceService memService;
	
	@Inject
	private LocalFilePersistenceService localStore;
	
	@Inject
	private DynamoDbPersistenceService ddb;
	
	@Inject
	private LocalCachedS3Service s3;
	
	@Inject
	private BillService billService;
	
	@Inject
	private BillInterpretationService billInterpreter;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private RollCallService rollCallService;
	
	@Inject
	private LegislatorInterpretationService legInterp;
	
	public static String getAiPrompt(CongressionalSession session, Party party, IssueStats stats) {
		val grade = stats.getLetterGrade();
		
		return PROMPT_TEMPLATE
				.replace("{{partyName}}", party.getName())
				.replace("{{session}}", String.valueOf(session.getNumber()))
				.replace("{{stats}}", stats.toString())
				.replace("{{letterGrade}}", grade)
				.replace("{{analysisType}}", grade.equals("A") || grade.equals("B") ? "endorsement" : (grade.equals("C") || grade.equals("D") ? "mixed analysis" : "harsh critique"))
				.replace("{{behavior}}", grade.equals("A") || grade.equals("B") ? "specific accomplishments" : (grade.equals("C") || grade.equals("D") ? "specific accomplishments or alarming behaviour" : "alarming behaviour"));
	}
}
