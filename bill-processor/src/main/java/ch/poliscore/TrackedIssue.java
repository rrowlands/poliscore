package ch.poliscore;

// Often called "Policy Issues" by our government
public enum TrackedIssue
{
	AgricultureAndFood("Agriculture and Food"),
	Education("Education"),
	Transportation("Transportation"),
	EconomicsAndCommerce("Economics and Commerce"),
	ForeignRelations("Foreign Relations"),
	SocialEquity("Social Equity"),
	Government("Government Efficiency and Management"),
	Healthcare("Healthcare"),
	Housing("Housing"),
	Energy("Energy"),
	Technology("Technology"),
	Immigration("immigration"),
	NationalDefense("National Defense"),
	CrimeAndLawEnforcement("Crime and Law Enforcement"),
	WildlifeAndForestManagement("Wildlife and Forest Management"),
	PublicLandsAndNaturalResources("Public Lands and Natural Resources"),
	EnvironmentalManagementAndClimateChange("Environmental Management and Climate Change"),
	OverallBenefitToSociety("Overall Benefit to Society");
	
	private String name;
	
	private TrackedIssue(String name)
	{
		this.name = name;
	}
	
	public String getName()
	{
		return this.name;
	}
}