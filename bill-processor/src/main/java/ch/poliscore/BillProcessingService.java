package ch.poliscore;

import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.IOUtils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BillProcessingService {

	@Inject
	private AIService ai;
	
	final String prompt = """
			Please score the following bill on the estimated impact upon the following sectors of the United States, rated from -10 (very harmful) to 0 (neutral) to +10 (very helpful). Please format your response as a list in the example format:
			
			1. Education: <score>
			2. Transportation: <score>
			3. Economy: <score>
			4. Climate change: <score>
			5. Overall benefit to society: <score>
			
			The text of the bill is as follows:
			""";
	
    public String process(String url) {
    	String billText;
    	try {
			billText = IOUtils.toString(new URL(url), "UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
    	
    	return ai.Chat(prompt + billText);
    }
}
