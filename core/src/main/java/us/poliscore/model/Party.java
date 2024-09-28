package us.poliscore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public enum Party {
	REPUBLICAN("Republican"),
	DEMOCRAT("Democrat"),
	INDEPENDENT("Independent");
	
	private String name;
	
	private Party(String name) {
		this.name = name;
	}
	
	@JsonIgnore
	public String getName() {
		return this.name;
	}
	
	public static Party from(String p) {
		if (p == null) return INDEPENDENT;
		
		if (p.trim().toLowerCase().equals("republican")) {
			return REPUBLICAN;
		} else if (p.trim().toLowerCase().equals("democrat")) {
			return DEMOCRAT;
		} else {
			return INDEPENDENT;
		}
	}
}
