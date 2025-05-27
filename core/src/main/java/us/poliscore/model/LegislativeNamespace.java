package us.poliscore.model;

import java.util.Arrays;

import lombok.Getter;

@Getter
public enum LegislativeNamespace {
	US_CONGRESS("us/congress"),
	US_CALIFORNIA("us/california"),
	US_TEXAS("us/texas"),
	US_NEW_YORK("us/newyork");
	
	private String namespace;
	
	private LegislativeNamespace(String namespace)
	{
		this.namespace = namespace;
	}
	
	public static LegislativeNamespace of(String namespace)
	{
		return Arrays.asList(LegislativeNamespace.values()).stream().filter(n -> n.getNamespace().equals(namespace)).findFirst().get();
	}
}
