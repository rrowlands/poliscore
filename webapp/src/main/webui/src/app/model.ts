
export interface Bill {
  impact: number;
  impactAbs: number;
  name: string;
  number: number;
  type: string;
  session: number;
  sponsor: BillSponsor;
  cosponsors: BillSponsor[];
  introducedDate: string;
  lastActionDate: string;
  id?: string;
  billId?: string;
  interpretation: BillInterpretation;
  rating?: number;
  confidence?: number;
  hot?: number;
  shortExplain?: string;
  status: { description: string, progress: number, sourceStatus: string };
}

export interface BillInterpretation {
  issueStats: IssueStats;
  id: string;
  genBillTitle: string;
  shortExplain: string;
  longExplain: string;
  riders: string[];
  billId: string;
  pressInterps: PressInterpretation[];
  metadata: BillMetadata;
}

export interface PressInterpretation {
  sentiment: number;
  id: string;
  genArticleTitle: string;
  shortExplain: string;
  longExplain: string;
  billId: string;
  metadata: BillMetadata;
}

export interface BillMetadata {
  model: string;
  date: string;
}

export interface BillSponsor {
  name: LegislatorName;
  legislatorId: string;
}

export interface BillInteraction {
  "@type": string;
  billName: any;
  billId: string;
  issueStats: IssueStats;
  date: string;
  voteStatus?: string;
  statusProgress: number;
}

export interface PartyStats extends IssueStats {
  party: string;
  longExplain: string;
  mostImportantBills: Bill[];
  leastImportantBills: Bill[];
  bestBills: Bill[];
  worstBills: Bill[];
  bestLegislators: Legislator[];
  worstLegislators: Legislator[];
}

export interface SessionStats {
  session: number;
  // partyStats: {
  //   [key: string]: PartyStats;
  // };
  democrat: PartyStats;
  republican: PartyStats;
  independent: PartyStats;
}

export class LegislatorName {
  first!: string;
  last!: string;
  official_full!: string;
}

export class Legislator {
    name!: LegislatorName;
    id?: string;
    legislatorId?: string;
    bioguideId?: string;
    thomasId?: string;
    interpretation?: LegislatorInterpretation;
    interactions?: BillInteraction[];
    terms!: [{
      chamber: string,
      startDate: string,
      endDate: string,
      state: string,
      district: number,
      party: string,
      url: string,
    }];
    photoError: boolean = false;
    birthday: string | undefined;
    impact!: number;
    impactAbs!: number;
    hot!: number;
}

export interface LegislatorPageData {
  location: string;
  legislators: Legislator[];
  allLegislators: [string, string][];
}

export interface LegislatorInterpretation {
    issueStats: IssueStats;
    legislatorId: string;
    metadata: any;
    longExplain: string;
    shortExplain: string;
}

export interface IssueStats {
    stats: any;
}

export type PageIndex = "ObjectsByLocation" | "ObjectsByDate" | "ObjectsByRating" | "ObjectsByRatingAbs" | "TrackedIssue" | "ObjectsByImpact" | "ObjectsByImpactAbs" | "ObjectsByIssueImpact" | "ObjectsByIssueRating" | "ObjectsByHot";

export class Page {
  index!: PageIndex;
  ascending?: boolean;
  pageSize?: number;
  exclusiveStartKey?: string | number;
  sortKey?: string;
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
    "Immigration": "Immigration and Border Security",
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

export function hasValidInterpretation(obj: any): boolean {
  return obj != null
    && obj.interpretation != null && obj.interpretation.issueStats != null && obj.interpretation.issueStats.stats != null
    && Object.entries(obj.interpretation.issueStats.stats).length > 0
    && Object.entries(obj.interpretation.issueStats.stats).filter(kv => kv[0] === "OverallBenefitToSociety").length === 1
    && obj.interpretation.longExplain != null;
}

export function getBenefitToSocietyIssue(issueStats: IssueStats): [string, number] {
  return Object.entries(issueStats?.stats).filter(kv => kv[0] === "OverallBenefitToSociety")[0] as [string, number];
}

export function gradeForStats(issueStats: IssueStats): string {
  if (issueStats == null || getBenefitToSocietyIssue(issueStats) == null) return "?";

  let rating = getBenefitToSocietyIssue(issueStats)[1];

  return gradeForRating(rating);
}

export function gradeForRating(rating: number): string {
  if (rating >= 40) return "A";
  else if (rating >= 30 && rating < 40) return "B";
  else if (rating >= 15 && rating < 30) return "C";
  else if (rating >= 0 && rating < 15) return "D";
  else if (rating < 0) return "F";
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

export const states: [string,string][] = [
  ['Alabama', 'AL'],
  ['Alaska', 'AK'],
  ['American Samoa', 'AS'],
  ['Arizona', 'AZ'],
  ['Arkansas', 'AR'],
  ['Armed Forces Americas', 'AA'],
  ['Armed Forces Europe', 'AE'],
  ['Armed Forces Pacific', 'AP'],
  ['California', 'CA'],
  ['Colorado', 'CO'],
  ['Connecticut', 'CT'],
  ['Delaware', 'DE'],
  ['District Of Columbia', 'DC'],
  ['Florida', 'FL'],
  ['Georgia', 'GA'],
  ['Guam', 'GU'],
  ['Hawaii', 'HI'],
  ['Idaho', 'ID'],
  ['Illinois', 'IL'],
  ['Indiana', 'IN'],
  ['Iowa', 'IA'],
  ['Kansas', 'KS'],
  ['Kentucky', 'KY'],
  ['Louisiana', 'LA'],
  ['Maine', 'ME'],
  ['Marshall Islands', 'MH'],
  ['Northern Mariana Islands', 'MP'],
  ['Maryland', 'MD'],
  ['Massachusetts', 'MA'],
  ['Michigan', 'MI'],
  ['Minnesota', 'MN'],
  ['Mississippi', 'MS'],
  ['Missouri', 'MO'],
  ['Montana', 'MT'],
  ['Nebraska', 'NE'],
  ['Nevada', 'NV'],
  ['New Hampshire', 'NH'],
  ['New Jersey', 'NJ'],
  ['New Mexico', 'NM'],
  ['New York', 'NY'],
  ['North Carolina', 'NC'],
  ['North Dakota', 'ND'],
  ['Northern Mariana Islands', 'NP'],
  ['Ohio', 'OH'],
  ['Oklahoma', 'OK'],
  ['Oregon', 'OR'],
  ['Pennsylvania', 'PA'],
  ['Puerto Rico', 'PR'],
  ['Rhode Island', 'RI'],
  ['South Carolina', 'SC'],
  ['South Dakota', 'SD'],
  ['Tennessee', 'TN'],
  ['Texas', 'TX'],
  ['US Virgin Islands', 'VI'],
  ['Utah', 'UT'],
  ['Vermont', 'VT'],
  ['Virginia', 'VA'],
  ['Washington', 'WA'],
  ['West Virginia', 'WV'],
  ['Wisconsin', 'WI'],
  ['Wyoming', 'WY'],
];

export const issueMap = {
    AgricultureAndFood: 'Agriculture and Food',
    Education: 'Education',
    Transportation: 'Transportation',
    EconomicsAndCommerce: 'Economics and Commerce',
    ForeignRelations: 'Foreign Relations',
    Government: 'Government Efficiency and Management',
    Healthcare: 'Healthcare',
    Housing: 'Housing',
    Energy: 'Energy',
    Technology: 'Technology',
    Immigration: 'Immigration and Border Security',
    NationalDefense: 'National Defense',
    CrimeAndLawEnforcement: 'Crime and Law Enforcement',
    WildlifeAndForestManagement: 'Wildlife And Forest Management',
    PublicLandsAndNaturalResources: 'Public Lands And Natural Resources',
    EnvironmentalManagementAndClimateChange: 'Environmental Management And Climate Change'
  };

export default function convertStateCodeToName(input: string): string {
  const toAbbr = input.length !== 2;

  // So happy that Canada and the US have distinct abbreviations
  const provinces: [string, string][] = [
    ['Alberta', 'AB'],
    ['British Columbia', 'BC'],
    ['Manitoba', 'MB'],
    ['New Brunswick', 'NB'],
    ['Newfoundland', 'NF'],
    ['Northwest Territory', 'NT'],
    ['Nova Scotia', 'NS'],
    ['Nunavut', 'NU'],
    ['Ontario', 'ON'],
    ['Prince Edward Island', 'PE'],
    ['Quebec', 'QC'],
    ['Saskatchewan', 'SK'],
    ['Yukon', 'YT'],
  ];

  const regions = states.concat(provinces);

  let i; // Reusable loop variable

  if (toAbbr) {
    input = input.replace(/\w\S*/g, function (txt: string) {
      return txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase();
    });
    for (i = 0; i < regions.length; i++) {
      if (regions[i][0] === input) {
        return regions[i][1];
      }
    }
  } else {
    input = input.toUpperCase();
    for (i = 0; i < regions.length; i++) {
      if (regions[i][1] === input) {
        return regions[i][0];
      }
    }
  }

  return input;
}
