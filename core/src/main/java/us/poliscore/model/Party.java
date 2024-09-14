package us.poliscore.model;

public enum Party {
	REPUBLICAN,
	DEMOCRAT,
	INDEPENDENT;
	
	private Party() {
		
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
