package us.poliscore.model;

import lombok.Getter;

@Getter
public enum LegislativeNamespace {
	US_CONGRESS("us/congress");
	
	private String namespace;
	
	private LegislativeNamespace(String namespace)
	{
		this.namespace = namespace;
	}
}
