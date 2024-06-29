
export class Legislator {
    name!: {first: string, last: string, official_full: string};
    id!: string;
    bioguideId?: string;
    thomasId?: string;
    interpretation?: LegislatorInterpretation
    interactions?: [{
      billName: any;
      billId: string;
      issueStats: IssueStats;
      date: string;
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
    "Immigration": "Immigration",
    "NationalDefense": "National Defense",
    "CrimeAndLawEnforcement": "Crime and Law Enforcement",
    "WildlifeAndForestManagement": "Wildlife And Forest Management",
    "PublicLandsAndNaturalResources": "Public Lands And Natural Resources",
    "EnvironmentalManagementAndClimateChange": "Environmental Management And Climate Change",
    "OverallBenefitToSociety": "Overall Benefit To Society"
  };

  return map[key];
}

export function getBenefitToSocietyIssue(issueStats: IssueStats): [string, number] {
  return Object.entries(issueStats?.stats).filter(kv => kv[0] === "OverallBenefitToSociety")[0] as [string, number];
}

export function gradeForStats(issueStats: IssueStats): string {
  let credit = getBenefitToSocietyIssue(issueStats)[1];

  if (credit >= 50) return "A";
  else if (credit >= 30 && credit < 50) return "B";
  else if (credit >= 10 && credit < 30) return "C";
  else if (credit > 0 && credit < 10) return "D";
  else if (credit <= 0) return "F";
  else return "?";
}
