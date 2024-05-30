package ch.poliscore.service;

import io.quarkus.test.Mock;

@Mock
public class MockAIService extends AIService {
	
	public static String response;
	
	public static void setResponse(String resp)
	{
		response = resp;
	}
	
	@Override
	public String Chat(String systemMsg, String userMsg)
    {
		return response;
    }
}
