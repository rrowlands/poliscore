package us.poliscore.model;

// Often called "Policy Issues" by our government
public enum TrackedIssue
{
	AgricultureAndFood,
	Education,
	Transportation,
	EconomicsAndCommerce,
	ForeignRelations,
//	SocialEquity,
	Government,
	Healthcare,
	Housing,
	Energy,
	Technology,
	Immigration,
	NationalDefense,
	CrimeAndLawEnforcement,
	WildlifeAndForestManagement,
	PublicLandsAndNaturalResources,
	EnvironmentalManagementAndClimateChange,
//	Feasibility("Feasibility"),
	OverallBenefitToSociety;
	
	public String getName()
	{
		if (this.equals(AgricultureAndFood))
		{
			return "Agriculture and Food";
		}
		else if (this.equals(Education))
		{
			return "Education";
		}
		else if (this.equals(Transportation))
		{
			return "Transportation";
		}
		else if (this.equals(EconomicsAndCommerce))
		{
			return "Economics and Commerce";
		}
		else if (this.equals(ForeignRelations))
		{
			return "Foreign Relations";
		}
//		else if (this.equals(SocialEquity))
//		{
//			return "Social Equity";
//		}
		else if (this.equals(Government))
		{
			return "Government Efficiency and Management";
		}
		else if (this.equals(Healthcare))
		{
			return "Healthcare";
		}
		else if (this.equals(Housing))
		{
			return "Housing";
		}
		else if (this.equals(Energy))
		{
			return "Energy";
		}
		else if (this.equals(Technology))
		{
			return "Technology";
		}
		else if (this.equals(Immigration))
		{
			return "Immigration And Border Security";
		}
		else if (this.equals(NationalDefense))
		{
			return "National Defense";
		}
		else if (this.equals(CrimeAndLawEnforcement))
		{
			return "Crime and Law Enforcement";
		}
		else if (this.equals(WildlifeAndForestManagement))
		{
			return "Wildlife and Forest Management";
		}
		else if (this.equals(PublicLandsAndNaturalResources))
		{
			return "Public Lands and Natural Resources";
		}
		else if (this.equals(EnvironmentalManagementAndClimateChange))
		{
			return "Environmental Management and Climate Change";
		}
		else if (this.equals(OverallBenefitToSociety))
		{
			return "Overall Benefit to Society";
		}
		
		return "unknown";
	}
}