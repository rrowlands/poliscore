import { Component, OnInit } from '@angular/core';
import { AppService } from '../app.service';
import { Legislator, issueKeyToLabel } from '../model';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import {MatCardModule} from '@angular/material/card'; 

@Component({
  selector: 'app-legislators',
  standalone: true,
  imports: [HttpClientModule, KeyValuePipe, CommonModule, RouterModule, MatCardModule],
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
    var issueStats = Object.entries(leg?.interpretation?.issueStats?.stats)
      .filter(kv => kv[0] != "OverallBenefitToSociety")
      .sort((a,b) => (b[1] as number) - (a[1] as number))
      .map(kv => issueKeyToLabel(kv[0]));

    issueStats = issueStats.slice(0, Math.min(3, issueStats.length));

    return "Focuses on " + issueStats.join(", ");
  }

  gradeForLegislator(leg: Legislator): string
  {
    let credit = Object.entries(leg?.interpretation?.issueStats?.stats).filter(kv => kv[0] === "OverallBenefitToSociety")[0][1] as number;

    if (credit >= 50) return "A";
    else if (credit >= 30 && credit < 50) return "B";
    else if (credit >= 10 && credit < 30) return "C";
    else if (credit > 0 && credit < 10) return "D";
    else if (credit <= 0) return "F";
    else return "Not enough data";
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

  colorForGrade(grade: string): string
  {
    const map: {[key: string]: string} = {
      A: "#1a9641",
      B: "#a6d96a",
      C: "rgb(179, 179, 0)",
      D: "#fdae61",
      F: "#d7191c"
    };

    return map[grade];
  }

}