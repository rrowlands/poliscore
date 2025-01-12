package us.poliscore.model;

import lombok.Getter;

@Getter
public enum LegislativeChamber {
	SENATE("Senate"),
	HOUSE("House");
	
	private String name;
	
	private LegislativeChamber(String name)
	{
		this.name = name;
	}
}
