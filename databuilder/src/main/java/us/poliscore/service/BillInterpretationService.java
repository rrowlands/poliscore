package us.poliscore.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NonNull;
import lombok.val;
import us.poliscore.MissingBillTextException;
import us.poliscore.model.AISliceInterpretationMetadata;
import us.poliscore.model.IssueStats;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillInterpretationParser;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.parsing.BillSlicer;
import us.poliscore.parsing.XMLBillSlicer;
import us.poliscore.service.storage.CachedS3Service;

/**
 * Two different interpretation strategies were tested for interpreting very large bills in "slices".
 * 
 * A) Summarize each slice and then interpret the summaries and ask AI to generate stats based on the summaries
 * B) Generate a small summary and also stats for each slice. Ask AI to summarize the summaries and then average all the stats for the final bill stats
 * 
 * These strategies were tested on BIL/us/congress/118/hr/8580 and it was determined that scenario A resulted in less accurate overall stats due to the fact
 * that each individual summary resulted in somewhat of a "telephone game" effect which resulted in a more "muted" outcome and which was less present when
 * averaging the stats from each slice.
 * 
 * Experimentations have also been made around concise versus longer responses, however the conclusion (with ChatGPT 4o) is that prompts without the "concise"
 * keyword tend to include a lot of wordy "filler" content without exposing much additional useful information from the bill. Longer form responses also suffer
 * from "header" content (the AI likes to have a paragraph for each tracked issue with a *** header *** format), however this can be avoided with a "include a
 * report without headers" phrasing.
 */
@ApplicationScoped
public class BillInterpretationService {

	// Test bills:
	// 1. Riders (7 identified) - https://poliscore.us/bill/118/hr/4365
	// 2. Good for america, bad for the world - https://poliscore.us/bill/118/hr/2336
	// 3. Controversial bill - https://poliscore.us/bill/118/s/1409
	// 4. Beetle bill - https://www.congress.gov/bill/118th-congress/senate-bill/3838
	
	public static final String statsPromptTemplate = """
			You will be given the text of a United States bill currently in congress. Your role is to be a non-partisan oversight committee, evaluating whether or not the following bill will produce a positive overall benefit to society. In your response, fill out the sections as listed in the following template. Each section will have detailed instructions on how to fill it out. Make sure to include the section title (such as, 'Stats:') in your response. Do not include the section instructions in your response.

			Stats:
			Score the following bill on the estimated impact to the United States upon the following criteria, rated from -100 (very harmful) to 0 (neutral) to +100 (very helpful) or N/A if it is not relevant. If the bill includes riders, please score it negatively.
			
			{issuesList}
			
			Bill Title:
			Write the bill title. If the bill does not have a title and is only referred to by its bill number (such as HR 4141), please make up a very short title for the bill based on its content.
			
			Riders:
			- List of bill riders identified and the section they occured at, or 'None' if there are none
			
			Short Report:
			A single paragraph, at least four sentence report which gives a detailed, but not repetitive, summary of the bill, any high level goals, and it's expected impact to society. If riders are present in the bill mention them and the section they occur at here, otherwise do not mention their absence. Do not include any formatting text, such as stars or dashes. Do not include non-human readable text such as XML ids.
			
			Long Report:
			A detailed, but not repetitive, report of the bill which references concrete, notable and specific text of the bill where possible. Make sure to explain: an overall summary of the bill; the high level goals the bill is attempting to achieve, and how it plans to achieve those goals; the impact to society the bill would have, if enacted. Your audience here is general public layman voters, so if you think they won't understand an acronym or a complex topic, please explain it. Should be between one and seven paragraphs long, depending on the complexity of the bill and the topics it covers. If the bill touches on controversial topics such as trans issues or guns rights, please include the advocating logic by proponents and also the advocating logic of the opposition, otherwise do not include this logic. Where relevant, cite scientific studies or the opinions of authoritative knowledge sources to provide more context. Keep in mind that we're trying to figure out how to spend U.S. taxpayer dollars: budgetary concerns are important. If their are riders in the bill, mention them in this summary, otherwise do not mention their absence. Do not include any formatting text, such as stars or dashes. Do not include non-human readable text such as XML ids.
			""";
	
	public static final String slicePromptTemplate = """
			You will be given the text of a United States bill currently in congress. Your role is to be a non-partisan oversight committee, evaluating whether or not the following bill will produce a positive overall benefit to society. In your response, fill out the sections as listed in the following template. Each section will have detailed instructions on how to fill it out. Make sure to include the section title (such as, 'Stats:') in your response. Do not include the section instructions in your response.

			Stats:
			Score the following bill on the estimated impact to the United States upon the following criteria, rated from -100 (very harmful) to 0 (neutral) to +100 (very helpful) or N/A if it is not relevant. If the bill includes riders, please score it negatively.
			
			{issuesList}
			
			Short Report:
			A single paragraph, at least four sentence report which gives a detailed, but not repetitive, summary of the bill, any high level goals, and it's expected impact to society. If riders are present in the bill mention them and the section they occur at here, otherwise do not mention their absence. Do not include any formatting text, such as stars or dashes. Do not include non-human readable text such as XML ids.
			""";
	
	public static final String aggregatePrompt = """
			A large U.S. congressional bill has been split into sections and summarized. Your role is to be a non-partisan oversight committee, evaluating whether or not the following bill will produce a positive overall benefit to society. In your response, fill out the sections as listed in the following template. Each section will have detailed instructions on how to fill it out. Make sure to include the section title (such as, 'Stats:') in your response. Do not include the section instructions in your response.
			
			Bill Title:
			Write the bill title. If the bill does not have a title and is only referred to by its bill number (such as HR 4141), please make up a very short title for the bill based on its content.
			
			Riders:
			- List of bill riders identified and the section they occured at, or 'None' if there are none
			
			Short Report:
			A single paragraph, at least four sentence report which gives a detailed, but not repetitive, summary of the bill, any high level goals, and it's expected impact to society. If riders are present in the bill mention them and the section they occur at here, otherwise do not mention their absence. Do not include any formatting text, such as stars or dashes. Do not include non-human readable text such as XML ids.
			
			Long Report:
			A detailed, but not repetitive, report of the bill which references concrete, notable and specific text of the bill where possible. Make sure to explain: an overall summary of the bill; the high level goals the bill is attempting to achieve, and how it plans to achieve those goals; the impact to society the bill would have, if enacted. Your audience here is general public layman voters, so if you think they won't understand an acronym or a complex topic, please explain it. Should be between one and seven paragraphs long, depending on the complexity of the bill and the topics it covers. If the bill touches on controversial topics such as trans issues or guns rights, please include the advocating logic by proponents and also the advocating logic of the opposition, otherwise do not include this logic. Where relevant, cite scientific studies or the opinions of authoritative knowledge sources to provide more context. Keep in mind that we're trying to figure out how to spend U.S. taxpayer dollars: budgetary concerns are important. If their are riders in the bill, mention them in this summary, otherwise do not mention their absence. Do not include any formatting text, such as stars or dashes. Do not include non-human readable text such as XML ids.
			""";
	
	
	public static final String statsPrompt;
	public static final String slicePrompt;
	static {
		String issues = String.join("\n", Arrays.stream(TrackedIssue.values()).map(issue -> issue.getName() + ": <score or N/A>").toList());
    	statsPrompt = statsPromptTemplate.replaceFirst("\\{issuesList\\}", issues);
    	slicePrompt = slicePromptTemplate.replaceFirst("\\{issuesList\\}", issues);
	}
	
	@Inject
	protected OpenAIService ai;
	
	@Inject
	protected CachedS3Service s3;
	
	@Inject
	protected BillService billService;
	
	public Optional<BillInterpretation> getByBillId(String billId)
	{
		return s3.get(BillInterpretation.generateId(billId, null), BillInterpretation.class);
	}
	
	public BillInterpretation getOrCreate(String billId)
	{
		val bill = billService.getById(billId).orElseThrow();
		val interpId = BillInterpretation.generateId(bill.getId(), null);
		val cached = s3.get(interpId, BillInterpretation.class);
		
		if (cached.isPresent())
		{
			return cached.get();
		}
		else
		{
			val interp = interpret(bill);
			
			return interp;
		}
	}
	
	protected BillInterpretation interpret(Bill bill) throws MissingBillTextException
	{
		Log.info("Interpreting bill " + bill.getId() + " " + bill.getName());
		
		val billText = billService.getBillText(bill).orElseThrow(() -> new MissingBillTextException());
		
		bill.setText(billText);
		
		if (billText.getXml().length() >= BillSlicer.MAX_SECTION_LENGTH)
    	{
    		List<BillSlice> slices = new XMLBillSlicer().slice(bill, bill.getText(), BillSlicer.MAX_SECTION_LENGTH);
    		List<AISliceInterpretationMetadata> sliceMetadata = new ArrayList<AISliceInterpretationMetadata>();
    		List<BillInterpretation> sliceInterps = new ArrayList<BillInterpretation>();
    		
    		if (slices.size() == 0) throw new UnsupportedOperationException("Slicer returned zero slices?");
    		else if (slices.size() == 1) {
    			bill.getText().setXml(slices.get(0).getText()); // TODO : Hackity hack. This achieves our goal of treating it as the bill text but it's not actually xml
    		} else {
    			IssueStats billStats = new IssueStats();
    			List<String> aggregateExplain = new ArrayList<String>();
        		
        		for (int i = 0; i < slices.size(); ++i)
        		{
        			BillSlice slice = slices.get(i);
        			
        			BillInterpretation sliceInterp = getOrCreateInterpretation(bill, slice);
        			
        			billStats = billStats.sum(sliceInterp.getIssueStats());
        			sliceMetadata.add((AISliceInterpretationMetadata) sliceInterp.getMetadata());
        			
        			sliceInterps.add(sliceInterp);
        			aggregateExplain.add(sliceInterp.getShortExplain());
        		}
        		
        		billStats = billStats.divideByTotalSummed();
        		
        		var bi = getOrCreateAggregateInterpretation(bill, billStats, String.join("\n", aggregateExplain), sliceInterps);
        		
        		return bi;
    		}
    	}
		
		var bi = getOrCreateInterpretation(bill, null);
		
    	return bi;
	}
	
	protected BillInterpretation getOrCreateAggregateInterpretation(Bill bill, IssueStats aggregateStats, String aggregateExplain, List<BillInterpretation> sliceInterps)
	{
		BillInterpretation bi = new BillInterpretation();
		bi.setBill(bill);
		
		bi.setMetadata(OpenAIService.metadata());
		bi.setSliceInterpretations(sliceInterps);
		
		String aiOut = ai.chat(aggregatePrompt, aggregateExplain);
		new BillInterpretationParser(bi).parse(aiOut);
		
		bi.setIssueStats(aggregateStats);
		bi.setId(BillInterpretation.generateId(bill.getId(), null));
		
		archive(bi);
		
		return bi;
	}
	
	protected BillInterpretation getOrCreateInterpretation(Bill bill, BillSlice slice)
	{
		val id = BillInterpretation.generateId(bill.getId(), slice == null ? null : slice.getSliceIndex());
		val cached = s3.get(id, BillInterpretation.class);
		
		if (cached.isPresent())
		{
			return cached.get();
		}
		else
		{
			BillInterpretation bi = new BillInterpretation();
			bi.setBill(bill);
			
			String interpText;
			if (slice == null)
			{
				interpText = ai.chat(slicePrompt, bill.getText().getXml());
				bi.setMetadata(OpenAIService.metadata());
			}
			else
			{
				interpText = ai.chat(statsPrompt, slice.getText());
				bi.setMetadata(OpenAIService.metadata(slice));
			}
			
			new BillInterpretationParser(bi).parse(interpText);
			bi.setId(id);
			
			archive(bi);
			
			return bi;
		}
	}
	
    protected void archive(BillInterpretation interp)
    {
    	s3.put(interp);
    }

	public boolean isInterpreted(@NonNull String billId) {
		val id = BillInterpretation.generateId(billId, null);
		return s3.exists(id, BillInterpretation.class);
	}
	
	public boolean isInterpreted(@NonNull String billId, int sliceIndex) {
		val id = BillInterpretation.generateId(billId, sliceIndex);
		return s3.exists(id, BillInterpretation.class);
	}
}
