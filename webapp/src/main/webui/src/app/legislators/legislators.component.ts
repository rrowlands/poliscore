import { Component, HostListener, OnInit } from '@angular/core';
import { AppService } from '../app.service';
import convertStateCodeToName, { Legislator, gradeForStats, issueKeyToLabel, colorForGrade, issueKeyToLabelSmall, subtitleForStats, Page, states, getBenefitToSocietyIssue } from '../model';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import {MatCardModule} from '@angular/material/card'; 
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import {MatButtonToggleModule} from '@angular/material/button-toggle'; 
import {MatAutocompleteModule} from '@angular/material/autocomplete';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Observable, map, startWith } from 'rxjs';

@Component({
  selector: 'app-legislators',
  standalone: true,
  imports: [HttpClientModule, KeyValuePipe, CommonModule, RouterModule, MatCardModule, MatPaginatorModule, MatButtonToggleModule, MatAutocompleteModule, ReactiveFormsModule],
  providers: [AppService, HttpClient],
  templateUrl: './legislators.component.html',
  styleUrl: './legislators.component.scss'
})
export class LegislatorsComponent implements OnInit {

  legs?: Legislator[];

  allLegislators: [string, string][] = [];

  searchOptions: [string, string][] = [];

  myControl = new FormControl('');

  filteredOptions?: Observable<[string, string][]>;

  public hasMoreContent: boolean = true;

  public isRequestingData: boolean = false;

  private lastScrollTop = 0;

  public page: Page = {
    index: "ObjectsByLocation",
    ascending: false,
    pageSize: 25
  };

  constructor(private service: AppService, private router: Router, private route: ActivatedRoute) {}

  ngOnInit(): void
  {
    this.isRequestingData = true;

    this.service.getLegislatorPageData().then(data => {
      this.legs = data.legislators;
      this.allLegislators = data.allLegislators;
      this.searchOptions = data.allLegislators.concat(states.map(s => ["STATE/" + s[1], s[0]]));
    }).finally(() => {
      this.isRequestingData = false;
    });

    this.filteredOptions = this.myControl.valueChanges.pipe(
      startWith(''),
      map(value => this._filter(value || '')),
    );
  }

  private _filter(value: string): [string, string][] {
    const filterValue = value.toLowerCase();

    // levenshtein apparently doesn't work for this search usecase? 
    // return this.allLegislators.sort((a,b) => 
    //   levenshtein(a[1].toLowerCase(), filterValue.toLowerCase())
    //   - levenshtein(b[1].toLowerCase(), filterValue.toLowerCase())
    // ).slice(0, 15);

    return this.searchOptions.filter(leg => leg[1].toLowerCase().includes(filterValue.toLowerCase()));
  }

  @HostListener('window:scroll', ['$event'])
  onScroll(e: any) {
    const el = e.target.documentElement;

    // Ignore scrolling upwards
    if (el.scrollTop < this.lastScrollTop){
        return;
    } else {
      this.lastScrollTop = el.scrollTop <= 0 ? 0 : el.scrollTop;
    }

    // Trigger when scrolled to bottom
    if (el.offsetHeight + el.scrollTop >= (el.scrollHeight - 200) && this.legs != null) {
      const lastLeg = this.legs[this.legs.length - 1];
      const sep = "~`~";

      if (this.page.index === "ObjectsByDate") {
        this.page.exclusiveStartKey = lastLeg.id + sep + lastLeg.birthday;
      } else if (this.page.index === "ObjectsByRating") {
        this.page.exclusiveStartKey = lastLeg.id + sep + getBenefitToSocietyIssue(lastLeg.interpretation!.issueStats)[1];
      } else if (this.page.index === "ObjectsByLocation") {
        this.page.exclusiveStartKey = lastLeg.id + sep + lastLeg.terms[0].state;
      } else {
        console.log("Unknown page index: " + this.page.index);
        return
      }

      this.fetchData(false);
    }
  }

  onSelectAutocomplete(bioguideId: string) {
    if (bioguideId.startsWith("STATE/")) {
      this.page.index = "ObjectsByLocation";
      this.page.sortKey = bioguideId.substring(6);
      this.page.ascending = true;
      this.fetchData();
      this.myControl.setValue("");
    } else {
      this.router.navigate(['/legislator', "LEG/us/congress/" + bioguideId]);
    }
  }

  togglePage(index: "ObjectsByDate" | "ObjectsByRating" | "ObjectsByLocation") {
    this.page.ascending = (index == this.page.index) ? !this.page.ascending : false;
    this.page.index = index;
    this.page.exclusiveStartKey = undefined;
    this.hasMoreContent = true;

    this.legs = [];

    this.fetchData();
  }

  fetchData(replace: boolean = true) {
    if (!this.hasMoreContent) return;

    this.isRequestingData = true;

    this.service.getLegislators(this.page).then(legs => {
      if (replace) {
        this.legs = legs;
      } else if (legs.length > 0 && this.legs?.findIndex(l => l.id == legs[0].id) == -1) {
        this.legs = this.legs.concat(legs);
      }

      if (legs.length == 0) {
        this.hasMoreContent = false;
      }
    }).finally(() => {
      this.isRequestingData = false;
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
    // return subtitleForStats(leg.interpretation?.issueStats!);

    let term = leg.terms[leg.terms.length - 1];

    return (term.chamber == "SENATE" ? "Senator" : "House") + " (" + convertStateCodeToName(term.state) + ")";
  }

}
