import { Bill, gradeForRating, gradeForStats } from "./model";

export function descriptionForBill(bill: Bill): string
{
    if (bill.shortExplain) {
        return bill.shortExplain.substring(0, 300) + "...";
    }

    return bill.interpretation.shortExplain.substring(0, 300) + "...";
}

  // descriptionForBill(bill: Bill): string
  // {
  //   var issueStats: any = Object.entries(bill?.interpretation?.issueStats?.stats)
  //     .filter(kv => kv[0] != "OverallBenefitToSociety")
  //     .sort((a,b) => Math.abs(b[1] as number) - Math.abs(a[1] as number))
  //     // .map(kv => issueKeyToLabel(kv[0]));

  //   if (window.innerWidth < 480) {
  //     issueStats = issueStats.map((kv: any) => issueKeyToLabelSmall(kv[0]));
  //   } else {
  //     issueStats = issueStats.map((kv: any) => issueKeyToLabel(kv[0]));
  //   }

  //   issueStats = issueStats.slice(0, Math.min(3, issueStats.length));

  //   return "Focuses on " + issueStats.join(", ");
  // }

 export function gradeForBill(bill: Bill): string
{
    if (bill.rating) {
        return gradeForRating(bill.rating);
    }

    return gradeForStats(bill.interpretation?.issueStats!);
}

export function subtitleForBill(bill: Bill): string
{
// return subtitleForStats(leg.interpretation?.issueStats!);

// let term = bill.terms[bill.terms.length - 1];

// return (term.chamber == "SENATE" ? "Senator" : "House") + " (" + convertStateCodeToName(term.state) + ")";

    let chamber = bill.type.toLowerCase().startsWith("s") ? "Senate" : "House";

    if (bill.sponsor.name.official_full)
        return "Sponsor: " + bill.sponsor.name.official_full + " (" + chamber + ")";
    else
        return "Sponsor: " + bill.sponsor.name + " (" + chamber + ")";
}
