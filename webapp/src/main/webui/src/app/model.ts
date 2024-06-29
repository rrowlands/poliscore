
export class Legislator {
    name!: {first: string, last: string, official_full: string};
    id!: string;
    bioguideId?: string;
    thomasId?: string;
    interpretation?: LegislatorInterpretation
    interactions?: [{
      billId: string;
      issueStats: IssueStats;
      date: [number, number, number]
    }];
}

export class LegislatorInterpretation {
    issueStats!: IssueStats;
    legislatorId!: string;
    metadata!: any;
}

export class IssueStats {
    stats: any;
    explanation!: string;
}

export function issueKeyToLabel(key: string): string
{
  const map: {[key: string]: string} = {
    "AgricultureAndFood": "Agriculture and Food",
    "Education": "Education",
    "Transportation": "Transportation",
    "EconomicsAndCommerce": "Economics and Commerce",
    "ForeignRelations": "Foreign Relations",
    "SocialEquity": "Social Equity",
    "Government": "Government",
    "Healthcare": "Healthcare",
    "Housing": "Housing",
    "Energy": "Energy",
    "Technology": "Technology",
    "Immigration": "Immigaration",
    "NationalDefense": "National Defense",
    "CrimeAndLawEnforcement": "Crime and Law Enforcement",
    "WildlifeAndForestManagement": "Wildlife And Forest Management",
    "PublicLandsAndNaturalResources": "Public Lands And Natural Resources",
    "EnvironmentalManagementAndClimateChange": "Environmental Management And Climate Change",
    "OverallBenefitToSociety": "Overall Benefit To Society"
  };

  return map[key];
}
