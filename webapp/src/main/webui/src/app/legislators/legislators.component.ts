import { Component, OnInit } from '@angular/core';
import { AppService } from '../app.service';
import { Legislator, gradeForStats, issueKeyToLabel, colorForGrade, issueKeyToLabelSmall } from '../model';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import {MatCardModule} from '@angular/material/card'; 
import { MatPaginatorModule } from '@angular/material/paginator';

@Component({
  selector: 'app-legislators',
  standalone: true,
  imports: [HttpClientModule, KeyValuePipe, CommonModule, RouterModule, MatCardModule, MatPaginatorModule],
  providers: [AppService, HttpClient],
  templateUrl: './legislators.component.html',
  styleUrl: './legislators.component.scss'
})
export class LegislatorsComponent implements OnInit {

  legs?: Legislator[];

  constructor(private service: AppService, private router: Router, private route: ActivatedRoute) {}

  ngOnInit(): void
  {
    this.service.getLegislators().then(legs => {
      this.legs = legs;
    });
  }

  routeTo(leg: Legislator)
  {
    document.getElementById(leg.id)?.classList.add("tran-div");
    this.router.navigate(['/legislator', leg.id]);
  }

  descriptionForLegislator(leg: Legislator): string
  {
    var issueStats: any = Object.entries(leg?.interpretation?.issueStats?.stats)
      .filter(kv => kv[0] != "OverallBenefitToSociety")
      .sort((a,b) => Math.abs(b[1] as number) - Math.abs(a[1] as number))
      // .map(kv => issueKeyToLabel(kv[0]));

    if (window.innerWidth < 480) {
      issueStats = issueStats.map((kv: any) => issueKeyToLabelSmall(kv[0]));
    } else {
      issueStats = issueStats.map((kv: any) => issueKeyToLabel(kv[0]));
    }

    issueStats = issueStats.slice(0, Math.min(3, issueStats.length));

    return "Focuses on " + issueStats.join(", ");
  }

  gradeForLegislator(leg: Legislator): string
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

  colorForGrade(grade: string): string {
    return colorForGrade(grade);
  }

  subtitleForLegislator(leg: Legislator): string
  {
    let credit = Object.entries(leg?.interpretation?.issueStats?.stats).filter(kv => kv[0] === "OverallBenefitToSociety")[0][1] as number;

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

}
