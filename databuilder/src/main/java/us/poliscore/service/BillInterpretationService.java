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
	
	// TODO : 
	// 1. Do not include the bill name in the summary. This is causing challenges on the legislator bill linking side if the name specified in the summary differs from what's in our database
	// 2. Could consider changing it to three paragraphs
	// 3. If the bill has a name like "HJRES-95", and no official title, get AI to create one on the fly.
	
	
	// Score the following bill (or bill section) on the estimated impact to the United States upon the following criteria, rated from -100 (very harmful) to 0 (neutral) to +100 (very helpful) or N/A if it is not relevant. Please include the bill title in the section labeled 'Bill Title', but not the reports. If the bill does not have a title and is only referred to by its bill number (such as HR 4141), please make up a very short title for the bill based on its content. After the title, include two different reports at the end. The first report is a concise, single paragraph report which gives a brief summary of the bill and it's expected impact to society. The second report is a concise report of the bill which references concrete, notable and specific text of the bill where possible. This second report should be between one and seven paragraphs long, depending on the complexity of the bill and the topics it covers. In the long form report, make sure to explain: an overall, concise summary of the bill; the high level goals the bill is attempting to achieve, and how it plans to achieve those goals; the impact to society the bill would have, if enacted. If the bill touches on controversial topics such as trans issues or guns rights, please include in the long form report the advocating logic by proponents and also the advocating logic of the opposition. If the bill touches on topics which require advanced or expert knowledge to understand, please attempt to include in the long form report clarifying knowledge. Where relevant, cite scientific studies or the opinions of authoritative knowledge sources to provide more context. Keep in mind that we're trying to figure out how to spend U.S. taxpayer dollars: budgetary concerns are important, not an afterthought. Do not include any formatting text, such as stars or dashes. If you are at all uncertain how a bill should be scored or interpreted, please respond with 'I am uncertain how to grade this'. Please format your response as a list in the example format:
	// Bill Title:
	// <bill title>
	//
	// Short Form:
	// <Single paragraph concise report of the expected impact to society. Do not include proponents and opponents "both sides" logic.>
	//
	// Long Form:
	// <Report between one and seven paragraphs long, covering the topics mentioned above. Keep it concise and do not repeat yourself>
	
	public static final String statsPromptTemplate = """
			Score the following bill (or bill section) on the estimated impact to the United States upon the following criteria, rated from -100 (very harmful) to 0 (neutral) to +100 (very helpful) or N/A if it is not relevant. Please include the bill title in the section labeled 'Bill Title', but not the reports. If the bill does not have a title and is only referred to by its bill number (such as HR 4141), please make up a very short title for the bill based on its content. After the title, include two different reports at the end. The first report is a concise, single paragraph report which gives a brief summary of the bill and it's expected impact to society, briefly explaining why it received various scores. The second report is a concise report of the bill which references concrete, notable and specific text of the bill where possible. This second report should be between one and seven paragraphs long, depending on the complexity of the bill and the topics it covers. In the long form report, make sure to explain: an overall, concise summary of the bill; the high level goals the bill is attempting to achieve, and how it plans to achieve those goals; the impact to society the bill would have, if enacted. If the bill touches on controversial topics such as trans issues or guns rights, please include the advocating logic by proponents and also the advocating logic of the opposition. If the bill touches on topics which require advanced or expert knowledge to understand, please attempt to include clarifying knowledge. Where relevant, cite scientific studies or the opinions of authoritative knowledge sources to provide more context. Keep in mind that we're trying to figure out how to spend U.S. taxpayer dollars: budgetary concerns are important, not an afterthought. If you are at all uncertain how a bill should be scored or interpreted, please respond with 'I am uncertain how to grade this'. Please format your response as a list in the example format:

            {issuesList}
			
			Bill Title:
			<bill title>
			
			Short Form:
			<single paragraph concise report of the expected impact to society>
			
			Long Form:
			<report between one and seven paragraphs long, covering the topics mentioned above>
			""";
	
	public static final String statsPrompt;
	static {
		String issues = String.join("\n", Arrays.stream(TrackedIssue.values()).map(issue -> issue.getName() + ": <score or N/A>").toList());
    	statsPrompt = statsPromptTemplate.replaceFirst("\\{issuesList\\}", issues);
	}
	
	public static final String slicePrompt = "Score the following bill (or bill section) on the estimated impact to the United States upon the following criteria, rated from -100 (very harmful) to 0 (neutral) to +100 (very helpful) or N/A if it is not relevant. Please include the bill title in the section labeled 'Bill Title', but not the reports. If the bill does not have a title and is only referred to by its bill number (such as HR 4141), please make up a very short title for the bill based on its content. After the title, include two different reports at the end. The first report is a concise, single paragraph report which gives a brief summary of the bill and it's expected impact to society, briefly explaining why it received various scores. The second report is a concise report of the bill which references concrete, notable and specific text of the bill where possible. This second report should be between one and seven paragraphs long, depending on the complexity of the bill and the topics it covers. In the long form report, make sure to explain: an overall, concise summary of the bill; the high level goals the bill is attempting to achieve, and how it plans to achieve those goals; the impact to society the bill would have, if enacted. If the bill touches on controversial topics such as trans issues or guns rights, please include the advocating logic by proponents and also the advocating logic of the opposition. If the bill touches on topics which require advanced or expert knowledge to understand, please attempt to include clarifying knowledge. Where relevant, cite scientific studies or the opinions of authoritative knowledge sources to provide more context. Keep in mind that we're trying to figure out how to spend U.S. taxpayer dollars: budgetary concerns are important, not an afterthought. If you are at all uncertain how a bill should be scored or interpreted, please respond with 'I am uncertain how to grade this'. Please format your response as a list in the example format:";
	
	public static final String aggregatePrompt = "Evaluate the impact to society of the following summarized bill text in a concise (single paragraph) report. In your report, please attempt to reference concrete, notable and specific text of the summarized bill where possible.";
	
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
        		
        		for (int i = 0; i < slices.size(); ++i)
        		{
        			BillSlice slice = slices.get(i);
        			
        			BillInterpretation sliceInterp = getOrCreateInterpretation(bill, slice);
        			
        			billStats = billStats.sum(sliceInterp.getIssueStats());
        			sliceMetadata.add((AISliceInterpretationMetadata) sliceInterp.getMetadata());
        			
        			sliceInterps.add(sliceInterp);
        		}
        		
        		billStats = billStats.divideByTotalSummed();
        		
        		var bi = getOrCreateAggregateInterpretation(bill, billStats, sliceInterps);
        		
        		return bi;
    		}
    	}
		
		var bi = getOrCreateInterpretation(bill, null);
		
    	return bi;
	}
	
	protected BillInterpretation getOrCreateAggregateInterpretation(Bill bill, IssueStats aggregateStats, List<BillInterpretation> sliceInterps)
	{
		BillInterpretation bi = new BillInterpretation();
		bi.setBill(bill);
		
		bi.setMetadata(OpenAIService.metadata());
		bi.setSliceInterpretations(sliceInterps);
		
		aggregateStats.setExplanation(ai.chat(summaryPrompt, aggregateStats.getExplanation()));
		
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
				interpText = ai.chat(statsPrompt, bill.getText().getXml());
				bi.setMetadata(OpenAIService.metadata());
			}
			else
			{
				interpText = ai.chat(statsPrompt, slice.getText());
				bi.setMetadata(OpenAIService.metadata(slice));
			}
			
			bi.setIssueStats(IssueStats.parse(interpText));
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
