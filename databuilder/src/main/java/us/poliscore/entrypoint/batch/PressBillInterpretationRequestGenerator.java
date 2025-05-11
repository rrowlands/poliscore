package us.poliscore.entrypoint.batch;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
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
import us.poliscore.ai.BatchOpenAIRequest.CustomOriginData;
import us.poliscore.model.AIInterpretationMetadata;
import us.poliscore.model.InterpretationOrigin;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.press.PressInterpretation;
import us.poliscore.press.BillArticleRecognizer;
import us.poliscore.press.GoogleSearchResponse;
import us.poliscore.press.RedditFetcher;
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
public class PressBillInterpretationRequestGenerator implements QuarkusApplication {
	
	// Google requires us to define this within their console, and it includes some configuration options such as how much of the web we want to search.
	public static final String GOOGLE_CUSTOM_SEARCH_ENGINE_ID = "3564aa93769fe4c0f";
	
	// Google's max queries on free tier is 100
	public static final int MAX_QUERIES = 100;
	
	private static final String PRESS_INTERPRETATION_PROMPT_TEMPLATE = """
			You will be given what is suspected, but not guaranteed, to be a press article which contains information about the following United States bill currently in congress.
			{{billIdentifier}}
			
			The first thing you must determine is if this text offers any interesting or useful analysis, information or an organization's stance about the bill in question. We are looking for information beyond what can be easily scraped from congress, so information such as basic voting information, an introduction date, a bill title, or simply an announcement that a bill was introduced or has passed does NOT count.
			
			If the provided text is NOT an interpretation of this bill or if the interpretation is of a different bill, you are to immediately respond as 'NO_INTERPRETATION - <reason>' where <reason> is the reason why you don't think it's a valid interpretation for this bill and EXIT.
			
			*IMPORTANT* If the text states that a particular organization either supports or does not support the bill in question, DO NOT return 'NO_INTERPRETATION'. The analysis does NOT have to be a detailed one, a simply stated opinion from a reputable organization is enough.
			
			Generally speaking, the workflow is as follows:
			1. Is the article actually about this bill? If not, respond 'NO_INTERPRETATION - <reason>'
			2. Does the article say anything useful? An easy yes here would be: informative conversation threads containing analysis, a pundit analysis, a press release containing an endorsement or condemntation, or an article by a major news organization about a bill. If the article does not contain useful information, respond as 'NO_INTERPRETATION - <reason>'.
			3. Otherwise, fill out the response template as specified below.
			
			IF you determine this is a valid interpretation of the bill, then your instructions are as follows:
			
			You are part of a non-partisan oversight committee, tasked to read and summarize the provided analysis, focusing especially on any predictions the analysis might make towards the bill's impact to society, as well as any explanations or high-level logic as to how or why. If possible, please include information about the author as well as any organization they may be a part of. In your response, fill out the sections as listed in the following template. Each section will have detailed instructions on how to fill it out. Make sure to include the section title (such as, 'Summary:') in your response. Do not include the section instructions in your response. Do not include any formatting text, such as stars or dashes. Do not include non-human readable text such as XML ids.
			
			If you are given a social media conversation (such as Reddit) you should keep in mind that the conversation may represent a lens through which to view a sizeable chunk of the population's relationship with a particular bill. In this scenario, you should consider comments which confidently state concrete opinions (and exclude exploratory comments) to generate an interpretation of the bill. Do not make any mention in your summary of comments being "highly upvoted". In your summary, provide a summary of the "public discussion" found in the thread.
			
			=== BEGIN response template ===
			
			Author:
			Write the name of the organization and/or author responsible for drafting the analysis, or N/A if it is not clear from the text. If the text has both an author and an organization, write the organization followed by the first / last name of the author (e.g. 'New York Times - Joe Schmoe'). If the text comes from social media and there is a singular author, you may write the name of the user and the website it was written (e.g. 'Reddit - <username>'). If the text was taken from social media and there is more than one author, you shall only place the name of the social media website (e.g. 'Twitter'). In the case of Reddit, please include the subreddit (e.g. 'Reddit (r/politics). 
			
			Title:
			Provide a "cleaned up" title from the title you were provided. This title should remove duplicative or unnecessary information, such as the author (as that will be listed separately), and it should aim to be concise without removing essential information.
			
			Sentiment:
			Provide a number from -100 to 100 which summarizes the article's opinion of the bill. If the article recommends voting against the bill, these scores should be negative, otherwise if it recommends voting for the bill they should be positive.
			
			Short Report:
			A one sentence summary of the text. Do not include any formatting text, such as stars or dashes. Do not include non-human readable text such as XML ids.
			
			Long Report:
			A detailed, but not repetitive summary of the analysis which references concrete, notable and specific text of the analysis where possible. This report be no longer than three paragraphs and should explain the author's opinion or stance on the bill, any high level goals, and it's predictions of the bill's expected impact to society (if any). Do not include any formatting text, such as stars or dashes. Do not include non-human readable text such as XML ids.
			
			Confidence:
			A self-rated number from 0 to 100 measuring how confident you are that your analysis was valid and interpreted correctly.
			
			=== END response template ===
			
			Multishot prompt examples:
			
			==USER==
			title: Congress Extends Medicare Telehealth Authority Through September
			url: https://www.afw.com/telehealth/news/hr1968
			
			Congress recently passed H.R. 1968, extending Medicare telehealth authority for audiologists and speech-language pathologists (SLPs) through September 30, 2025. AFW is actively working to secure permanent telehealth status by supporting the reintroduction of the Expanded Telehealth Access Act, which would permanently authorize audiologists, SLPs, physical therapists, and occupational therapists as Medicare telehealth providers. Advocates are encouraged to contact their representatives to push for this change. This extension affects Medicare only and does not impact Medicaid telehealth coverage, which remains state-specific.
			
			==AGENT==
			NO_INTERPRETATION - The article mentions that H.R. 1968 was passed, but it does not provide a concrete opinion either for or against the bill. Instead, it offers an opinion about a different bill. This is more of a news update than it is a bill interpretation.
			
			==USER==
			title: MEALS ON WHEELS AMERICA ISSUES STATEMENT ON PASSAGE OF H.R. 1968, FULL-YEAR CONTINUING APPROPRIATIONS AND EXTENSIONS ACT, 2025
			url: https://www.mow.com/learn-more/national/press-room/news/2025/hr1968
			
			ARLINGTON, Va., March 14, 2025 – Meals on Wheels America President and CEO Ellie Hollander today issued this statement in response to the passage of H.R. 1968, Full-Year Continuing Appropriations and Extensions Act, 2025 on March 14, 2025: 
			Meals on Wheels America is deeply disappointed that Congress has failed yet again to prioritize the needs of our nation’s older adults by passing a long-term continuing resolution (CR) instead of comprehensive spending bills that could have made critical investments to address the growing crises of senior hunger and isolation.  
			This CR extends government funding through September 30, 2025. A government shutdown is never the desired outcome, but the flat funding levels set in this CR represent, in effect, a funding cut that could lead to a reduction in services, given rising costs and increased need among a rapidly growing senior population. This represents a missed opportunity to prioritize seniors and will further strain the nationwide network of Meals on Wheels providers.  
			Even though Meals on Wheels serves more than 2 million seniors, we know that an additional 2.5 million likely need meals but are not receiving essential services, and 1 in 3 providers already has a waitlist. America’s seniors cannot wait any longer for lifesaving nutritious meals and moments of connection.  
			We remain committed to working with Congress and the administration to advocate for much-needed funding increases and stability for senior nutrition providers that have been operating under uncertain conditions for far too long. Meals on Wheels America urgently calls on Congress to increase funding for senior nutrition programs. 
			
			==AGENT==
			Author:
			Meals on Wheels
			
			Sentiment:
			-30
			
			Title:
			Meals on Wheels America Issues Statement on Passage of H.R. 1968
			
			Short Report:
			Meals on Wheels issues a press release stating that they are "deeply disappointed  that Congress has failed yet again to prioritize the needs of our nation’s older adults".
			
			Long Report:
			Meals on Wheels America, led by President and CEO Ellie Hollander, expresses strong disappointment with the passage of H.R. 1968, the Full-Year Continuing Appropriations and Extensions Act of 2025. The organization criticizes Congress for passing another continuing resolution (CR) instead of comprehensive spending bills that could have "made critical investments to address the growing crises of senior hunger and isolation." The main goal highlighted by the statement is securing greater, stable funding for senior nutrition programs, which Meals on Wheels argues are already under significant strain. Hollander warns that the flat funding levels in the CR, despite technically avoiding a government shutdown, effectively amount to a cut because of rising operational costs and an expanding senior population, predicting that this will lead to service reductions nationwide. The analysis emphasizes that over 2 million seniors are currently served by Meals on Wheels, yet an estimated additional 2.5 million seniors remain in need, with one in three providers maintaining waitlists. The organization asserts that "America’s seniors cannot wait any longer" and urges Congress to immediately prioritize increased funding. The broader societal impact predicted is worsening hunger, isolation, and health risks among older adults, unless substantial investments are made soon to stabilize and expand services.
			
			==USER==
			title: Another Win this Week for Common Sense – President Trump Signs CRA Resolution Nullifying Methane Tax Regulations on Energy Producers
			url: https://www.ipaa.org/another-win-this-week-for-common-sense

			IPAA Appreciates Quick Action by President on Hoeven and Pfluger Legislation 
			WASHINGTON – Independent Petroleum Association of America (IPAA) President & CEO Jeff Eshelman issued the following statement today on President Donald Trump signing H.J.Res.35 which through the Congressional Review Act process disapproves of the Biden Environmental Protection Agency’s (EPA) methane tax regulations:
			“In another win this week for common sense, President Trump and his Administration have taken action to nullify the regulations the Biden Administration established to implement the misguided methane tax on oil and natural gas producers. On Wednesday, EPA announced that the agency is reconsidering its Subpart OOOOb and Subpart OOOOc regulations and its Subpart W greenhouse gas reporting program rules – reconsideration provides a pathway for making these regulations more cost-effective and well-structured. IPAA appreciates President Trump moving quickly to sign this Congressional Review Act resolution and the initiative of Senator John Hoeven (R-ND) and Congressman August Pfluger (R-TX) in guiding the resolution through Congress.
			“IPAA and our members remain committed to working with the EPA to find a regulatory pathway designed for the sources it regulates, while encouraging continued progress toward reducing emissions. Big new oil and natural gas wells and low producing older wells have differing emissions profiles. Our members are making constant improvements to the technology being used to reduce, measure and report on emissions.”
			
			==AGENT==
			Author:
			IPAA (Independent Petroleum Association of America)
			
			Sentiment:
			100
			
			Title:
			Another Win this Week for Common Sense – President Trump Signs CRA Resolution Nullifying Methane Tax Regulations on Energy Producers
			
			Short Report:
			... excluded for mutlishot brevity ...
			
			Long Report:
			... excluded for mutlishot brevity ...
		""";
	
	// Prompt from when we were using BillInterpretation to store the data. No longer relevant since we're using PressInterpretation now.
	/*
	private static final String PRESS_INTERPRETATION_PROMPT_TEMPLATE = """
			You will be given what is suspected, but not guaranteed, to be an analysis of the following United States bill currently in congress.
			{{billIdentifier}}
			
			The first thing you must determine is if this text offers any interesting or useful analysis of the bill in question, and if this text would reasonably be considered as a "Bill Interpretation". A "Bill Interpretation" in this context, is one in which the author provides opinions, analysis, gives a voting recommendation and/or endorsement of a bill, or otherwise predicts an impact to society of a particular bill.
			
			Reddit conversations ARE generally considered valid for generating a bill interpretation. Do NOT respond 'NO_INTERPRETATION' unless the conversation is not actually about the bill.
			
			If the provided text is NOT an interpretation of this bill or if the interpretation is of a different bill, you are to immediately respond as 'NO_INTERPRETATION - <reason>' where <reason> is the reason why you don't think it's a valid interpretation for this bill and EXIT.
			
			IF you determine this is a valid interpretation of the bill, then your instructions are as follows:
			
			You are part of a non-partisan oversight committee, tasked to read and summarize the provided analysis, focusing especially on any predictions the analysis might make towards the bill's impact to society, as well as any explanations or high-level logic as to how or why. If possible, please include information about the author as well as any organization they may be a part of. In your response, fill out the sections as listed in the following template. Each section will have detailed instructions on how to fill it out. Make sure to include the section title (such as, 'Summary:') in your response. Do not include the section instructions in your response.
			
			If you are given a Reddit conversation you should keep in mind that the comments you are receiving are ones which have received a lot of upvotes, and as a result may be considered to represent a lens through which to view a sizeable chunk of the population's relationship with a particular bill. In this scenario, you should consider comments which confidently state concrete opinions (and exclude exploratory comments) to generate an interpretation of the bill. Do not make any mention in your summary of comments being "highly upvoted". In your summary, provide a summary of the "public discussion" found in the thread.
			
			=== BEGIN response template ===
			
			Author:
			Write the name of the organization and/or author responsible for drafting the analysis, or N/A if it is not clear from the text. If the text has both an author and an organization, write the organization followed by the first / last name of the author (e.g. 'New York Times - Joe Schmoe'). If the text comes from social media and there is a singular author, you may write the name of the user and the website it was written (e.g. 'Reddit - <username>'). If the text was taken from social media and there is more than one author, you shall only place the name of the social media website (e.g. 'Twitter'). In the case of Reddit, please include the subreddit (e.g. 'Reddit (r/politics). 
			
			Stats:
			Upon reading the interpretation, please write how you think the author would score the bill on the estimated impact to the United States upon the following criteria, rated from -100 (very harmful) to 0 (neutral) to +100 (very helpful) or N/A if it is not relevant. Your scoring here should be a sentiment analysis of the provided text, categorized by issue. If the analysis recommends voting against the bill, these scores should be negative, otherwise if it recommends voting for the bill they should be positive.
			
			{issuesList} 
			
			Short Report:
			A one sentence summary of the text.
			
			Long Report:
			A detailed, but not repetitive summary of the analysis which references concrete, notable and specific text of the analysis where possible. This report should explain the author's opinion or stance on the bill, any high level goals, and it's predictions of the bill's expected impact to society. Do not include any formatting text, such as stars or dashes. Do not include non-human readable text such as XML ids.
			
			Confidence:
			A self-rated number from 0 to 100 measuring how confident you are that your analysis was valid and interpreted correctly.
			
			=== END response template ===

			Multishot prompt examples:

			==USER==
			title: Congress Extends Medicare Telehealth Authority Through September
			url: https://www.afw.com/telehealth/news/hr1968
			
			Congress recently passed H.R. 1968, extending Medicare telehealth authority for audiologists and speech-language pathologists (SLPs) through September 30, 2025. AFW is actively working to secure permanent telehealth status by supporting the reintroduction of the Expanded Telehealth Access Act, which would permanently authorize audiologists, SLPs, physical therapists, and occupational therapists as Medicare telehealth providers. Advocates are encouraged to contact their representatives to push for this change. This extension affects Medicare only and does not impact Medicaid telehealth coverage, which remains state-specific.
			
			==AGENT==
			NO_INTERPRETATION - The article mentions that H.R. 1968 was passed, but it does not provide a concrete opinion either for or against the bill. Instead, it offers an opinion about a different bill. This is more of a news update than it is a bill interpretation.
			
			==USER==
			title: MEALS ON WHEELS AMERICA ISSUES STATEMENT ON PASSAGE OF H.R. 1968, FULL-YEAR CONTINUING APPROPRIATIONS AND EXTENSIONS ACT, 2025
			url: https://www.mow.com/learn-more/national/press-room/news/2025/hr1968
			
			ARLINGTON, Va., March 14, 2025 – Meals on Wheels America President and CEO Ellie Hollander today issued this statement in response to the passage of H.R. 1968, Full-Year Continuing Appropriations and Extensions Act, 2025 on March 14, 2025: 
			Meals on Wheels America is deeply disappointed that Congress has failed yet again to prioritize the needs of our nation’s older adults by passing a long-term continuing resolution (CR) instead of comprehensive spending bills that could have made critical investments to address the growing crises of senior hunger and isolation.  
			This CR extends government funding through September 30, 2025. A government shutdown is never the desired outcome, but the flat funding levels set in this CR represent, in effect, a funding cut that could lead to a reduction in services, given rising costs and increased need among a rapidly growing senior population. This represents a missed opportunity to prioritize seniors and will further strain the nationwide network of Meals on Wheels providers.  
			Even though Meals on Wheels serves more than 2 million seniors, we know that an additional 2.5 million likely need meals but are not receiving essential services, and 1 in 3 providers already has a waitlist. America’s seniors cannot wait any longer for lifesaving nutritious meals and moments of connection.  
			We remain committed to working with Congress and the administration to advocate for much-needed funding increases and stability for senior nutrition providers that have been operating under uncertain conditions for far too long. Meals on Wheels America urgently calls on Congress to increase funding for senior nutrition programs. 
			
			==AGENT==
			Author:
			Meals on Wheels
			
			Stats:
			Agriculture and Food: N/A
			Education: N/A
			Transportation: N/A
			Economics and Commerce: N/A
			Foreign Relations: N/A
			Government Efficiency and Management: N/A
			Healthcare: -30
			Housing: N/A
			Energy: N/A
			Technology: N/A
			Immigration And Border Security: N/A
			National Defense: N/A
			Crime and Law Enforcement: N/A
			Wildlife and Forest Management: N/A
			Public Lands and Natural Resources: N/A
			Environmental Management and Climate Change: N/A
			Overall Benefit to Society: -30
			
			Short Report:
			Meals on Wheels issues a press release stating that they are "deeply disappointed  that Congress has failed yet again to prioritize the needs of our nation’s older adults".
			
			Long Report:
			Meals on Wheels America, led by President and CEO Ellie Hollander, expresses strong disappointment with the passage of H.R. 1968, the Full-Year Continuing Appropriations and Extensions Act of 2025. The organization criticizes Congress for passing another continuing resolution (CR) instead of comprehensive spending bills that could have "made critical investments to address the growing crises of senior hunger and isolation." The main goal highlighted by the statement is securing greater, stable funding for senior nutrition programs, which Meals on Wheels argues are already under significant strain. Hollander warns that the flat funding levels in the CR, despite technically avoiding a government shutdown, effectively amount to a cut because of rising operational costs and an expanding senior population, predicting that this will lead to service reductions nationwide. The analysis emphasizes that over 2 million seniors are currently served by Meals on Wheels, yet an estimated additional 2.5 million seniors remain in need, with one in three providers maintaining waitlists. The organization asserts that "America’s seniors cannot wait any longer" and urges Congress to immediately prioritize increased funding. The broader societal impact predicted is worsening hunger, isolation, and health risks among older adults, unless substantial investments are made soon to stabilize and expand services.
			""";
	*/
	
	// Multi-shot Reddit convo. Was originally added to tell AI to parse reddit, but was replaced instead with an explanation at the beginning of the prompt (which was found to work better)
	/*
	 		==USER==
			title: What are the PROS and CONS of voting for H.R.1968 - Full-Year ...
			url: https://www.reddit.com/r/NeutralPolitics/comments/1jawsml/what_are_the_pros_and_cons_of_voting_for_hr1968/
			
			The following is a conversation thread scraped from Reddit. The right arrows at the start of each comment '>' indicate how nested the comment is within the current comment chain. If the comment does not start with any right arrows then it indicates the start of a comment chain.
			(OP) Hand0fMystery (2025-03-13 23:34:02)
			What are the PROS and CONS of voting for H.R.1968 - Full-Year Continuing Appropriations and Extensions Act, 2025, especially regarding constitutional checks and balances? Passing the CR could strengthen the Executive at the expense of Congress. A "no" vote might allow Congress to retain some control, but risks a shutdown. Thoughts?
			> candre23 (2025-03-14 05:05:26)
			Pro: the government continues operating.
			Cons: Democrats would show weakness by caving, Trump would gain unchecked control over spending, and Democratic voters would feel betrayed. A shutdown could have been framed as necessary to protect constitutional spending authority.
			>> Fargason (2025-03-14 07:00:26)
			Shutting down the government is not a win, especially with Democrats' approval at a record low.
			>>> Die-Nacht (2025-03-14 07:26:21)
			Their low approval stems from being seen as ineffective. Doing nothing won’t help.
			>>>> nosecohn (2025-03-14 11:30:12)
			Voting down a CR that maintains Biden-era funding could make Democrats look worse. Republicans put them in a tough spot.
			>>>>> breeresident (2025-03-14 12:22:09)
			Republicans hold the majority. If there's a shutdown, it's on them to negotiate, not just Democrats.
			nosecohn (2025-03-14 11:21:32)
			I'm conflicted. The CR largely preserves Biden's budget, which would have been welcomed earlier. However, it gives Trump leeway to damage agencies. Courts and public backlash are pushing back against Trump, but if that's insufficient, what is the Democrats' post-shutdown plan?
			wolfehr (2025-03-14 13:11:57)
			The CR cuts non-defense discretionary spending and gives Trump power to withhold funds. Cuts include: layoffs at the Department of Education, slashed Army Corps of Engineers funding, housing assistance reductions, threats to Social Security, infrastructure delays, nutrition aid cuts, and veterans' healthcare funding shortfalls.
			Vivecs954 (2025-03-14 13:31:23)
			The Education Department source doesn’t specifically state half of employees will be laid off.
			Master_of_Ritual (2025-03-14 13:55:01)
			Wasn’t there also a directive to cut $880 billion over 10 years? Was that changed before the bill passed?
			nosecohn (2025-03-14 16:02:08)
			The $880 billion cut is from a separate budget bill. The CR mainly extends prior funding levels with some Republican-driven cuts.
			Salt-League-6153 (2025-03-14 16:44:45)
			Without the CR, nonessential services would stop, and Trump would decide what's essential. Congress retains constitutional power, but must choose to exercise it.
			
			==AGENT==
			Author:
			Reddit (conversation thread on r/NeutralPolitics)
			
			Stats:
			Agriculture and Food: -30
			Education: -60
			Transportation: -40
			Economics and Commerce: -40
			Foreign Relations: N/A
			Government Efficiency and Management: -70
			Healthcare: -30
			Housing: -50
			Energy: N/A
			Technology: N/A
			Immigration And Border Security: N/A
			National Defense: N/A
			Crime and Law Enforcement: N/A
			Wildlife and Forest Management: N/A
			Public Lands and Natural Resources: -30
			Environmental Management and Climate Change: -20
			Overall Benefit to Society: -50
			
			Short Report:
			Reddit users on r/NeutralPolitics discuss the pros and cons of H.R.1968, with most expressing concern that the bill weakens congressional power, enables executive overreach, and imposes harmful funding cuts to key social programs, despite preventing a government shutdown.
			
			Long Report:
			The public discussion around H.R.1968 reveals a strong consensus that, while the bill prevents a government shutdown by continuing government operations, it does so at significant cost to constitutional balance and key public services. The central concern expressed by commenters is that the bill represents a shift in power from Congress to the Executive Branch, specifically granting former President Trump increased authority over federal spending decisions. This is widely seen as an erosion of Congress’s constitutional duty to control appropriations and a precedent that could undermine the checks and balances foundational to U.S. governance.
			
			Multiple comments emphasize that the bill grants the president discretion to withhold funds from agencies, which could lead to selective defunding and politicized implementation of federal programs. Specific consequences cited include workforce reductions at the Department of Education, severe funding cuts to the Army Corps of Engineers, decreased housing assistance that could lead to mass evictions, potential disruptions to Social Security administration operations, delays in infrastructure improvements, diminished nutrition aid, and underfunded veterans' healthcare services. While one user noted that the source for Education Department layoffs may not specify exact numbers, there is little dispute that meaningful cuts are embedded in the bill.
			
			The continuing resolution (CR) primarily extends Biden-era funding levels, but with several Republican-influenced reductions. Some users see this as a tactical trap for Democrats—preserving much of their preferred budget on paper, while enabling the Executive to sabotage its implementation. Commenters point out that the CR allows for the appearance of legislative continuity while weakening the actual operational capacity of the government.
			
			A significant tension in the discussion revolves around the trade-off between avoiding a shutdown and defending legislative authority. Critics of the bill argue that Democrats lost an opportunity to use the shutdown as a constitutional stand to reassert congressional control over spending. Others contend that with Democratic approval ratings already low, a shutdown would only worsen public perception of government dysfunction, particularly for a party already seen as ineffective.
			
			There is also division over political accountability. Some users argue that since Republicans hold the majority, they bear responsibility for any resulting shutdowns and funding chaos. However, others counter that voting against the CR—especially when it retains much of the prior administration’s budget—might appear politically reckless or hypocritical.
			
			In summary, the public discourse portrays H.R.1968 as a bill that achieves short-term government functionality at the cost of long-term institutional integrity. While it staves off a shutdown, it undermines Congress’s spending authority, empowers selective enforcement of federal programs, and imposes substantial cuts on essential social services. The bill is broadly characterized not as a compromise, but as a strategic retreat from democratic accountability and constitutional principle.
	 */
	
	
	public static final String PRESS_INTERPRETATION_PROMPT;
	static {
		String issues = String.join("\n", Arrays.stream(TrackedIssue.values()).map(issue -> issue.getName() + ": <score or N/A>").toList());
		PRESS_INTERPRETATION_PROMPT = PRESS_INTERPRETATION_PROMPT_TEMPLATE.replaceFirst("\\{issuesList\\}", issues);
	}
	
	public static String AI_MODEL = "gpt-4o";
	
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
	
	private int totalQueries = 0;
	
	private List<BatchOpenAIRequest> requests = new ArrayList<BatchOpenAIRequest>();
	
	private List<File> writtenFiles = new ArrayList<File>();
	
	private Set<Bill> dirtyBills = new HashSet<Bill>();
	
	private Set<Bill> queriedBills = new HashSet<Bill>();
	
	public static final String[] processBills = new String[] {
//			Bill.generateId(CongressionalSession.S119.getNumber(), BillType.HR, 1968),
//			Bill.generateId(CongressionalSession.S119.getNumber(), BillType.HJRES, 35),
//			Bill.generateId(CongressionalSession.S119.getNumber(), BillType.SJRES, 11),
//			Bill.generateId(CongressionalSession.S119.getNumber(), BillType.HJRES, 25),
//			Bill.generateId(CongressionalSession.S119.getNumber(), BillType.HR, 2480),
//			Bill.generateId(CongressionalSession.S119.getNumber(), BillType.S, 5)
	};
	
	public static AIInterpretationMetadata metadata()
	{
		return AIInterpretationMetadata.construct(OpenAIService.PROVIDER, AI_MODEL, 0);
	}
	
	@SneakyThrows
	public List<File> process()
	{
		Log.info("Scraping press articles");
		
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		s3.optimizeExists(BillText.class);
		s3.optimizeExists(PressInterpretation.class);
		s3.optimizeExists(BillInterpretation.class);
		
		int block = 1;
		tokenLen = 0;
		totalRequests = 0;
		requests = new ArrayList<BatchOpenAIRequest>();
		writtenFiles = new ArrayList<File>();
		
		for (Bill b : memService.query(Bill.class).stream().filter(b ->
				b.isIntroducedInSession(PoliscoreUtil.CURRENT_SESSION)
				&& s3.exists(BillText.generateId(b.getId()), BillText.class)
//				&& b.getIntroducedDate().isBefore(LocalDate.now().minus(10, ChronoUnit.DAYS)) // Must be at least x days old (otherwise there won't be press coverage) - Commented out. If we're going to pass the bill text through AI we might as well scan for press. Ideally this filter criteria would exactly match the bill request generator
			).sorted(Comparator.comparing(Bill::getDate).reversed()).collect(Collectors.toList())) {
			if (totalQueries >= MAX_QUERIES) break;
			
			// Don't interpret really old bills
			// TODO : Once we get all the old bills interpreted we can replace this with a filter where we just ignore bills older than 101 days. (we won't always need to check the interp's lastPressQuery so long as we keep on top of generation)
			if (b.getDate().isAfter(LocalDate.now().minus(101, ChronoUnit.DAYS))) {
				val interp = s3.get(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class);
				
				if (interp.isPresent() && interp.get().getLastPressQuery() != LocalDate.EPOCH) continue;
			}
			
//		for (String billId : processBills) { Bill b = memService.get(billId, Bill.class).get();
//			deleteExisting(b);
			try {
				processBill(b);
			} catch (GoogleQuotaExceededException ex) {
				Log.info("Hit google's search quota limit. Haulting further queries and returning.");
				break;
			}
		}
//		processOrigin(b, new InterpretationOrigin("url", "title"), Jsoup.parse(new File("/Users/rrowlands/dev/projects/poliscore/databuilder/src/main/resources/ace-ccr.html")));
//		processOrigin(b, new InterpretationOrigin("https://www.reddit.com/r/NeutralPolitics/comments/1jawsml/what_are_the_pros_and_cons_of_voting_for_hr1968", "What are the PROS and CONS of voting for H.R.1968 - Full-Year Continuing Appropriations and Extensions Act, 2025?"));
//		processOrigin(b, new InterpretationOrigin("https://www.asha.org/news/2025/congress-extends-medicare-telehealth-authority-through-september/", "Congress Extends Medicare Telehealth Authority Through September"));
//		processOrigin(b, new InterpretationOrigin("https://www.aamc.org/news/press-releases/aamc-statement-passage-full-year-continuing-resolution", "Medicare Telehealth Flexibilities Extended, but Without Promise of Permanent Solution"));
		
		writeBlock(block++);
		
		Log.info("Press scraper complete. Executed " + totalQueries + " Google search queries and generated " + totalRequests + " AI requests.");
		
		return writtenFiles;
	}
	
	public void deleteExisting(Bill b)
	{
		var pressInterps = billService.getAllPressInterps(b.getId());
		
		for (val interp : pressInterps)
		{
//			if (interp.getId().contains("reddit"))
				s3.delete(interp.getId(), PressInterpretation.class);
		}
		
		Log.info("Deleted " + pressInterps.size() + " existing interpretations");
	}
	
	
	/**
	 * @return Only bills for which an actual request has been generated
	 */
	public Set<Bill> getDirtyBills()
	{
		return dirtyBills;
	}
	
	/**
	 * 
	 * @return All bills which were queried during this run
	 */
	public Set<Bill> getQueriedBills() {
		return queriedBills;
	}
	
	@SneakyThrows
	private void processBill(Bill b) {
		var interp = s3.get(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class).orElse(null);
		
		if (interp != null && interp.getLastPressQuery().isAfter(LocalDate.now().minus(30, ChronoUnit.DAYS))) return; // Skip if it was interpreted in the last x days
		if (interp == null) interp = new BillInterpretation();
		
		final String typeAndNumber = b.getType().getName().toUpperCase() + " " + b.getNumber();
		
		String query;
		if (b.getName() == null || StringUtils.isEmpty(b.getName()) || b.getName().toLowerCase().replaceAll("[\\s\\.]+", "").equals(typeAndNumber.toLowerCase().replaceAll("[\\s\\.]+", "")))
		{
			query = typeAndNumber;
			
			if (b.getNamespace().equals(LegislativeNamespace.US_CONGRESS))
				query = "Congress " + query;
		}
		else
		{
			query = b.getType().getName().toUpperCase() + " " + b.getNumber() + " " + b.getName();
		}
		
	    val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
	    
	    boolean generatedRequest = false;

	    generatedRequest = fetchAndProcessSearchResults(b, encodedQuery, 1) || generatedRequest;
	    
	    // Fetch an extra page for laws
	    if (b.getStatus().getProgress() == 1.0f)
	    	generatedRequest = fetchAndProcessSearchResults(b, encodedQuery, 11) || generatedRequest;
	    
	    if (generatedRequest)
	    	dirtyBills.add(b);
	    
	    queriedBills.add(b);
	}
	
	public void recordLastPressQueries()
	{
		Log.info("Updating LastPressQuery for interpreted bills");
		
		for (var b : queriedBills) {
			val interp = s3.get(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class).orElse(null);
			
			if (interp != null) {
				interp.setLastPressQuery(LocalDate.now());
			    s3.put(interp);
			}
		}
	}

	@SneakyThrows
	private boolean fetchAndProcessSearchResults(Bill b, String encodedQuery, int startIndex) {
		if (totalQueries >= MAX_QUERIES) return false;
		
		Log.info("Performing google search for press articles [" + encodedQuery + "]");
		
	    final String url = "https://customsearch.googleapis.com/customsearch/v1?key=" + 
	                        secretService.getGoogleSearchSecret() + 
	                        "&cx=" + GOOGLE_CUSTOM_SEARCH_ENGINE_ID + 
	                        "&q=" + encodedQuery + 
	                        "&start=" + startIndex;

	    String sResp = fetchUrl(url);
	    val resp = new ObjectMapper().readValue(sResp, GoogleSearchResponse.class);

	    if (resp.getItems() == null) return false;
	    
	    boolean generatedRequest = false;

	    for (val item : resp.getItems())
		{
	    	try
	    	{
				if (!item.getLink().endsWith(".pdf") && StringUtils.isBlank(item.getFileFormat()))
				{
					val origin = new InterpretationOrigin(item.getLink(), item.getTitle());
					
					generatedRequest = processOrigin(b, origin) || generatedRequest;
				}
	        } catch (Exception e) {
	            Log.warn("General error connecting to " + item.getLink() + ": " + e.getMessage());
	        }
		}
	    
	    totalQueries++;
	    
	    return generatedRequest;
	}
	
	private boolean processOrigin(Bill b, InterpretationOrigin origin)
	{
		if (s3.exists(PressInterpretation.generateId(b.getId(), origin), PressInterpretation.class)) return false;
		
		String articleText = null;
		
		if (origin.getUrl().contains("reddit.com/"))
		{
			articleText = RedditFetcher.fetch(origin);
		}
		else
		{
			articleText = processHtmlOrigin(b, origin);
		}
		
		if (articleText != null)
		{
			return processArticle(b, origin, articleText);
		}
		
		return false;
	}
	
	@SneakyThrows
	private String processHtmlOrigin(Bill b, InterpretationOrigin origin)
	{
		var linkResp = Jsoup.connect(origin.getUrl()).followRedirects(true).userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:126.0) Gecko/20100101 Firefox/126.0").ignoreHttpErrors(true).execute();
		
		if (linkResp.statusCode() >= 200 && linkResp.statusCode() < 400)
		{
			var fetched = linkResp.parse();
			
			// Clean up the HTML to remove things we know we don't want to process
			var body = fetched.body();
			body.select("script,.hidden,style,noscript").remove(); // Strip scripts
			body.select("[style~=(?i)display:\\s*none|visibility:\\s*hidden|opacity:\\s*0]").remove(); // Strip hidden elements
			for (Node node : body.childNodes()) { if (node.nodeName().equals("#comment")) { node.remove(); } } // Strip comments
			body.select("nav, footer, header, aside").remove(); // Strip navigational content
			for (String className : new String[]{"navbar", "menu", "sidebar", "footer", "legal"}) { body.select("." + className).remove(); } // Strip common classes
			
//			var text = StringUtils.join(" ", body.nodeStream().filter(n -> n instanceof Element && ((Element)n).text().length() > 50).map(n -> ((Element)n).text()).toList());
			
			String articleText = body.text();
			
			return articleText;
		}
		
		return null;
	}
	
	protected boolean processArticle(Bill b, InterpretationOrigin origin, String articleText)
	{
		float confidence = BillArticleRecognizer.recognize(b, articleText, origin.getUrl());
		
//		Log.info("Confidence that " + origin.getUrl() + " is written about bill " + b.getId() + " resolved to " + confidence);
		
		if (confidence > 0.4f)
		{
			return interpretArticle(b, origin, articleText);
		}
		
		return false;
	}
	
	private boolean interpretArticle(Bill b, InterpretationOrigin origin, String body)
	{
		String oid = PressInterpretation.generateId(b.getId(), origin);
		var data = new CustomOriginData(origin, oid);
		
		String text = "title: " + origin.getTitle() + "\nurl: " + origin.getUrl() + "\n\n";
		
		text += body;
		if (text.length() > OpenAIService.MAX_REQUEST_LENGTH)
			text = text.substring(0, OpenAIService.MAX_REQUEST_LENGTH);
		
		var prompt = PRESS_INTERPRETATION_PROMPT.replace("{{billIdentifier}}", "United States, " + b.getSession() + "th Congress" + ", " + b.getOriginatingChamber().getName() + "\n" + b.getType().getName() + " " + b.getNumber()) + " - " + b.getName() + "\nIntroduced in " + b.getIntroducedDate();
		createRequest(data, prompt, text);
		
		return true;
	}
	
	private void createRequest(CustomOriginData data, String sysMsg, String userMsg) {
		if (userMsg.length() > OpenAIService.MAX_REQUEST_LENGTH) {
			throw new RuntimeException("Max user message length exceeded on " + data.getOid() + " (" + userMsg.length() + " > " + OpenAIService.MAX_REQUEST_LENGTH);
		}
		
		List<BatchBillMessage> messages = new ArrayList<BatchBillMessage>();
		messages.add(new BatchBillMessage("system", sysMsg));
		messages.add(new BatchBillMessage("user", userMsg));
		
		requests.add(new BatchOpenAIRequest(
				data,
				new BatchOpenAIBody(messages, AI_MODEL)
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
	private String fetchUrl(String url) {
	    final int maxRetries = 5;
	    final long baseDelayMillis = 1000; // 1 second base
	    int attempt = 0;

	    while (true) {
	        try {
	            HttpResponse<String> response = HttpClient.newHttpClient()
	                .send(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());

	            int status = response.statusCode();
	            String body = response.body();

	            if (status == 429 || body.contains("\"reason\":\"rateLimitExceeded\"")) {
	                throw new GoogleQuotaExceededException("Google Search API quota exceeded: " + body);
	            }

	            if (status >= 500 && status < 600) {
	                throw new IOException("Transient Google API error " + status + ": " + body);
	            }

	            if (status != 200) {
	                Log.error("Google API request failed: " + body);
	                throw new RuntimeException("Google API error " + status + ": " + body);
	            }

	            return body;
	        } catch (GoogleQuotaExceededException e) {
	            throw e; // don't retry, break immediately
	        } catch (IOException e) {
	            if (++attempt > maxRetries) {
	                Log.error("Max retries reached for Google API call. Giving up.");
	                throw e;
	            }
	            long delay = baseDelayMillis * (1L << (attempt - 1)); // exponential backoff
	            Log.warn("Google API call failed (attempt " + attempt + "): " + e.getMessage() + ". Retrying in " + delay + "ms...");
	            Thread.sleep(delay);
	        }
	    }
	}
	
	public class GoogleQuotaExceededException extends RuntimeException {
	    private static final long serialVersionUID = -1070397745717473884L;
		public GoogleQuotaExceededException(String message) {
	        super(message);
	    }
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
	
	public static void main(String[] args) {
		Quarkus.run(PressBillInterpretationRequestGenerator.class, args);
		Quarkus.asyncExit(0);
	}
}