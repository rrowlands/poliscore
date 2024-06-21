
export class Legislator {
    name!: {first: string, last: string, official_full: string};
    id!: string;
    bioguideId?: string;
    thomasId?: string;
    interpretation?: LegislatorInterpretation
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
