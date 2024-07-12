
export interface Bill {
  name: string;
  number: number;
  type: string;
  congress: number;
  sponsor: BillSponsor;
  cosponsors: BillSponsor[];
  introducedDate: string;
  id: string;
  interpretation: BillInterpretation;
}

export interface BillInterpretation {
  issueStats: IssueStats;
  id: string;
  billId: string;
  metadata: any;
}

export interface BillSponsor {
  name: string;
  bioguide_id: string;
}

export interface BillInteraction {
  "@type": string;
  billName: any;
  billId: string;
  issueStats: IssueStats;
  date: string;
  voteStatus?: string;
}

export class Legislator {
    name!: {first: string, last: string, official_full: string};
    id!: string;
    bioguideId?: string;
    thomasId?: string;
    interpretation?: LegislatorInterpretation;
    interactions?: [BillInteraction];
    terms!: [{
      chamber: string,
      startDate: string,
      endEnd: string,
      state: string,
      district: number,
      party: string,
      url: string,
    }];
    photoError: boolean = false;
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

export class Page {
  index?: string;
  ascending?: boolean;
  pageSize?: number;
  exclusiveStartKey?: string;
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

export function issueKeyToLabelSmall(key: string): string
{
  const map: {[key: string]: string} = {
    "AgricultureAndFood": "Agriculture",
    "Education": "Education",
    "Transportation": "Transportation",
    "EconomicsAndCommerce": "Economics",
    "ForeignRelations": "Foreign Relations",
    "SocialEquity": "Social Equity",
    "Government": "Government",
    "Healthcare": "Healthcare",
    "Housing": "Housing",
    "Energy": "Energy",
    "Technology": "Technology",
    "Immigration": "Immigration",
    "NationalDefense": "National Defense",
    "CrimeAndLawEnforcement": "Law Enforcement",
    "WildlifeAndForestManagement": "Forest Management",
    "PublicLandsAndNaturalResources": "Natural Resources",
    "EnvironmentalManagementAndClimateChange": "Environment",
    "OverallBenefitToSociety": "Benefit To Society"
  };

  return map[key];
}

export function getBenefitToSocietyIssue(issueStats: IssueStats): [string, number] {
  return Object.entries(issueStats?.stats).filter(kv => kv[0] === "OverallBenefitToSociety")[0] as [string, number];
}

export function gradeForStats(issueStats: IssueStats): string {
  if (issueStats == null || getBenefitToSocietyIssue(issueStats) == null) return "?";

  let credit = getBenefitToSocietyIssue(issueStats)[1];

  /*
  if (credit >= 30) return "A";
  else if (credit > 10 && credit < 30) return "B";
  else if (credit >= -10 && credit <= 10) return "C";
  else if (credit > -30 && credit < -10) return "D";
  else if (credit <= -30) return "F";
  else return "?";
  */

  if (credit >= 50) return "A";
  else if (credit >= 30 && credit < 50) return "B";
  else if (credit >= 10 && credit < 30) return "C";
  else if (credit >= 0 && credit < 10) return "D";
  else if (credit < 0) return "F";
  else return "?";
}

export function colorForGrade(grade: string): string
{
  const map: {[key: string]: string} = {
    A: "#1a9641",
    B: "#a6d96a",
    C: "rgb(179, 179, 0)",
    D: "#fdae61",
    F: "#d7191c",
    "?": "#ffffff"
  };

  return map[grade];
}

export function subtitleForStats(issueStats: IssueStats): string
{
  let credit = getBenefitToSocietyIssue(issueStats)[1];

  if (credit >= 50) return "Credit to humanity";
  else if (credit >= 30 && credit < 50) return "Fighting the good fight";
  else if (credit >= 15 && credit < 30) return "Positive";
  else if (credit >= 0 && credit < 30) return "Barely positive";
  else if (credit >= -15 && credit < 0) return "Slighty negative";
  else if (credit >= -30 && credit < 15) return "Negative";
  else if (credit > -50 && credit < -30) return "Public enemy";
  else if (credit <= -50) return "Menace to society";
  else return "Not enough data";
}
