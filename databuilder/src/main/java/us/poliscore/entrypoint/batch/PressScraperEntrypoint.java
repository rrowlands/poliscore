package us.poliscore.entrypoint.batch;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.Environment;
import us.poliscore.PoliscoreUtil;
import us.poliscore.ai.BatchOpenAIRequest;
import us.poliscore.ai.BatchOpenAIRequest.BatchBillMessage;
import us.poliscore.ai.BatchOpenAIRequest.BatchOpenAIBody;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.InterpretationOrigin;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillType;
import us.poliscore.press.BillArticleRecognizer;
import us.poliscore.press.GoogleSearchResponse;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.OpenAIService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.SecretService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryObjectService;

@QuarkusMain(name="PressScraperEntrypoint")
public class PressScraperEntrypoint implements QuarkusApplication {
	
	// Google requires us to define this within their console, and it includes some configuration options such as how much of the web we want to search.
	public static final String GOOGLE_CUSTOM_SEARCH_ENGINE_ID = "3564aa93769fe4c0f";
	
//	private static final String PRESS_INTERPRETATION_PROMPT_TEMPLATE = """
//			You will be given an analysis of a United States bill currently in congress. You are part of a non-partisan oversight committee, tasked to read and summarize the provided analysis, focusing especially on any predictions the analysis might make towards the bill's impact to society, as well as any explanations or high-level logic as to how or why. If possible, please include information about the author as well as any organization they may be a part of. In your response, fill out the sections as listed in the following template. Each section will have detailed instructions on how to fill it out. Make sure to include the section title (such as, 'Stats:') in your response. Do not include the section instructions in your response. Fill these sections out based purely on the information in the provided analysis and how you think the authors would fill out these stats, take special care not to introduce your own personal bias.
//			
//			Stats:
//			Based solely on the provided analysis, score the following bill on the estimated impact to the United States upon the following criteria, rated from -100 (very harmful) to 0 (neutral) to +100 (very helpful) or N/A if it is not relevant.
//			
//			{issuesList}
//			
//			Short Report:
//			A single paragraph, at least four sentence report which gives a detailed, but not repetitive, summary of the analysis, any high level goals, and it's predictions of the bill's expected impact to society. Do not include any formatting text, such as stars or dashes. Do not include non-human readable text such as XML ids.
//			
//			Long Report:
//			A detailed, but not repetitive, report of the analysis which references concrete, notable and specific text of the analysis where possible. Do not include any formatting text, such as stars or dashes. Do not include non-human readable text such as XML ids.
//			""";
	
	private static final String PRESS_INTERPRETATION_PROMPT_TEMPLATE = """
			You will be given what is suspected, but not guaranteed, to be an analysis of the following United States bill currently in congress.
			{{billIdentifier}}
			
			If the provided text is NOT an analysis of this bill, or if the analysis is of a different bill, you are to immediately respond 'NO_INTERPRETATION' and EXIT. If you determine this is a valid analysis of the bill, then your instructions are as follows. 
			
			You are part of a non-partisan oversight committee, tasked to read and summarize the provided analysis, focusing especially on any predictions the analysis might make towards the bill's impact to society, as well as any explanations or high-level logic as to how or why. If possible, please include information about the author as well as any organization they may be a part of. In your response, fill out the sections as listed in the following template. Each section will have detailed instructions on how to fill it out. Make sure to include the section title (such as, 'Summary:') in your response. Do not include the section instructions in your response.
			
			Organization or Author:
			Write the name of the organization or author responsible for drafting the analysis, or N/A if it is not clear from the text. If the text has both an author and an organization, write the organization instead of the first / last name of the author.
			
			Favorability:
			A number, from -100 (very unfavorable) to 0 (neutral) to 100 (very favorable), representing how favorable the analysis is of the bill in question. If the analysis recommends voting against the bill, this score should be negative, otherwise if it recommends voting for the bill it should be positive. 
			
			Short Report:
			A single paragraph concise report which gives a detailed, but not repetitive summary of the analysis, the author's opinion or stance on the bill, any high level goals, and it's predictions of the bill's expected impact to society.
			
			Long Report:
			A detailed, but not repetitive summary of the analysis which references concrete, notable and specific text of the analysis where possible. Do not include any formatting text, such as stars or dashes. Do not include non-human readable text such as XML ids.
			""";
	
	
	public static final String PRESS_INTERPRETATION_PROMPT;
	static {
		String issues = String.join("\n", Arrays.stream(TrackedIssue.values()).map(issue -> issue.getName() + ": <score or N/A>").toList());
		PRESS_INTERPRETATION_PROMPT = PRESS_INTERPRETATION_PROMPT_TEMPLATE.replaceFirst("\\{issuesList\\}", issues);
	}
	
	
	@Inject
	private MemoryObjectService memService;
	
	@Inject
	private LocalFilePersistenceService localStore;
	
	@Inject
	private DynamoDbPersistenceService ddb;
	
	@Inject
	private LocalCachedS3Service s3;
	
	@Inject
	private BillService billService;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private RollCallService rollCallService;
	
	@Inject
	private LegislatorInterpretationService legInterp;
	
	@Inject
	private SecretService secretService;
	
	private long tokenLen = 0;
	
	private long totalRequests = 0;
	
	private List<BatchOpenAIRequest> requests = new ArrayList<BatchOpenAIRequest>();
	
	private List<File> writtenFiles = new ArrayList<File>();
	
	@SneakyThrows
	public List<File> process()
	{
		Log.info("Scraping press articles");
		
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		int block = 1;
		tokenLen = 0;
		totalRequests = 0;
		requests = new ArrayList<BatchOpenAIRequest>();
		writtenFiles = new ArrayList<File>();
		
		
		Bill b = memService.get(Bill.generateId(CongressionalSession.S119.getNumber(), BillType.HR, 2984), Bill.class).get();
		
		processBill(b);
//		processOriginFetch(b, new InterpretationOrigin("url", "title"), Jsoup.parse(new File("/Users/rrowlands/dev/projects/poliscore/databuilder/src/main/resources/ace-ccr.html")));
		
		writeBlock(block++);
		
		Log.info("Press scraper complete. Generated " + totalRequests + " requests.");
		
		return writtenFiles;
	}
	
	@SneakyThrows
	private void processBill(Bill b)
	{
		final String query = b.getType().getName().toUpperCase() + " " + b.getNumber() + " " + b.getName();
		val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
		
		final String url = "https://customsearch.googleapis.com/customsearch/v1?key=" + secretService.getGoogleSearchSecret() + "&cx=" + GOOGLE_CUSTOM_SEARCH_ENGINE_ID + "&q=" + encodedQuery;
		String sResp = fetchUrl(url);
		
		val resp = new ObjectMapper().readValue(sResp, GoogleSearchResponse.class);
		
		for (val item : resp.getItems())
		{
			if (!item.getLink().endsWith(".pdf") && StringUtils.isBlank(item.getFileFormat()))
			{
				System.out.println(item.getLink());
				
				var linkResp = Jsoup.connect(item.getLink()).followRedirects(true).userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:126.0) Gecko/20100101 Firefox/126.0").ignoreHttpErrors(true).execute();
				
				if (linkResp.statusCode() >= 200 && linkResp.statusCode() < 400)
				{
					val origin = new InterpretationOrigin(item.getLink(), item.getTitle());
					
					processOriginFetch(b, origin, linkResp.parse());
				}
			}
		}
	}
	
	@SneakyThrows
	private void processOriginFetch(Bill b, InterpretationOrigin origin, Document fetched)
	{
		// Clean up the HTML to remove things we know we don't want to process
		var body = fetched.body();
		body.select("script,.hidden,style,noscript").remove(); // Strip scripts
		body.select("[style~=(?i)display:\\s*none|visibility:\\s*hidden|opacity:\\s*0]").remove(); // Strip hidden elements
		for (Node node : body.childNodes()) { if (node.nodeName().equals("#comment")) { node.remove(); } } // Strip comments
		body.select("nav, footer, header, aside").remove(); // Strip navigational content
		for (String className : new String[]{"navbar", "menu", "sidebar", "footer", "legal"}) { body.select("." + className).remove(); } // Strip common classes
		
//		var text = StringUtils.join(" ", body.nodeStream().filter(n -> n instanceof Element && ((Element)n).text().length() > 50).map(n -> ((Element)n).text()).toList());
		
		String articleText = body.text();
		
		float confidence = BillArticleRecognizer.recognize(b, articleText, origin.getUrl());
		
		System.out.println("Confidence that " + origin.getUrl() + " is written about bill " + b.getId() + " resolved to " + confidence);
		
		if (confidence > 0.4f)
		{
			interpretArticle(b, origin, articleText);
		}
	}
	
	private void interpretArticle(Bill b, InterpretationOrigin origin, String body)
	{
		String oid = BillInterpretation.generateId(b.getId(), origin, null);
		
		String text = "title: " + origin.getTitle() + "\nurl: " + origin.getUrl() + "\n\n";
		
		text += body;
		if (text.length() > OpenAIService.MAX_REQUEST_LENGTH)
			text = text.substring(0, OpenAIService.MAX_REQUEST_LENGTH);
		
		var prompt = PRESS_INTERPRETATION_PROMPT.replace("{{billIdentifier}}", b.getNamespace().getNamespace().replace("/", " ") + ", " + b.getOriginatingChamber().getName() + ", " + b.getType().getName() + " " + b.getNumber());
		createRequest(oid, prompt, text);
	}
	
	private void createRequest(String oid, String sysMsg, String userMsg) {
		if (userMsg.length() > OpenAIService.MAX_REQUEST_LENGTH) {
			throw new RuntimeException("Max user message length exceeded on " + oid + " (" + userMsg.length() + " > " + OpenAIService.MAX_REQUEST_LENGTH);
		}
		
		List<BatchBillMessage> messages = new ArrayList<BatchBillMessage>();
		messages.add(new BatchBillMessage("system", sysMsg));
		messages.add(new BatchBillMessage("user", userMsg));
		
		requests.add(new BatchOpenAIRequest(
				oid,
				new BatchOpenAIBody(messages)
		));
		
		tokenLen += (userMsg.length() / 4);
	}
	
	private void writeBlock(int block) throws IOException {
		if (requests.size() == 0) return;
		
		File f = requestFile(block);
		
		val mapper = PoliscoreUtil.getObjectMapper();
		val s = requests.stream().map(r -> {
			try {
				return mapper.writeValueAsString(r);
			} catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}
		}).toList();
		
		FileUtils.write(f, String.join("\n", s), "UTF-8");
		
		totalRequests += requests.size();
		
		Log.info("Successfully wrote " + requests.size() + " requests to " + f.getAbsolutePath());
		
		writtenFiles.add(f);
		
		requests = new ArrayList<BatchOpenAIRequest>();
		tokenLen = 0;
	}
	
	public static File requestFile(int blockNum) {
		return new File(Environment.getDeployedPath(), "openapi-press-bulk-" + blockNum + ".jsonl");
	}
	
	@SneakyThrows
	private String fetchUrl(String url)
	{
		return HttpClient.newHttpClient().send(HttpRequest.newBuilder().uri(java.net.URI.create(url)).build(), HttpResponse.BodyHandlers.ofString()).body();
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
	
	public static void main(String[] args) {
		Quarkus.run(PressScraperEntrypoint.class, args);
		Quarkus.asyncExit(0);
	}
}