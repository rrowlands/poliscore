import convertStateCodeToName, { gradeForStats, issueKeyToLabel, issueKeyToLabelSmall, Legislator } from "./model";

// 
export function descriptionForLegislator(leg: Legislator, small: boolean = false): string
  {
    var issueStats: any = Object.entries(leg?.interpretation?.issueStats?.stats)
      .filter(kv => kv[0] != "OverallBenefitToSociety")
      .sort((a,b) => Math.abs(b[1] as number) - Math.abs(a[1] as number))
      // .map(kv => issueKeyToLabel(kv[0]));
    
    if (small) {
      issueStats = issueStats.map((kv: any) => issueKeyToLabelSmall(kv[0]));
    } else {
      issueStats = issueStats.map((kv: any) => issueKeyToLabel(kv[0]));
    }

    issueStats = issueStats.slice(0, Math.min(3, issueStats.length));

    return "Focuses on " + issueStats.join(", ");
  }

  export function upForReelection(leg: Legislator) {
    return leg && leg!.terms![leg!.terms!.length - 1].endDate === (new Date().getFullYear() + 1) + '-01-03';
  }

  export function gradeForLegislator(leg: Legislator): string
  {
    /*
    let credit = Object.entries(leg?.interpretation?.issueStats?.stats).filter(kv => kv[0] === "OverallBenefitToSociety")[0][1] as number;

    if (credit >= 50) return "A";
    else if (credit >= 30 && credit < 50) return "B";
    else if (credit >= 10 && credit < 30) return "C";
    else if (credit > 0 && credit < 10) return "D";
    else if (credit <= 0) return "F";
    else return "Not enough data";
    */

    return gradeForStats(leg.interpretation?.issueStats!);
  }

  export function colorForGrade(grade: string): string {
    return colorForGrade(grade);
  }

  export function subtitleForLegislator(leg: Legislator): string
  {
    // return subtitleForStats(leg.interpretation?.issueStats!);

    let term = leg.terms[leg.terms.length - 1];

    return (term.chamber == "SENATE" ? "Senator" : "House") + " (" + convertStateCodeToName(term.state) + ")";
  }