import { Component, HostListener, OnInit } from '@angular/core';
import { AppService } from '../app.service';
import convertStateCodeToName, { Legislator, gradeForStats, issueKeyToLabel, colorForGrade, issueKeyToLabelSmall, subtitleForStats, Page, states, getBenefitToSocietyIssue, Bill } from '../model';
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
import { BillComponent } from '../bill/bill.component';

@Component({
  selector: 'bills',
  standalone: true,
  imports: [HttpClientModule, KeyValuePipe, CommonModule, RouterModule, MatCardModule, MatPaginatorModule, MatButtonToggleModule, MatAutocompleteModule, ReactiveFormsModule, MatButtonModule],
  providers: [AppService, HttpClient],
  templateUrl: './bills.component.html',
  styleUrl: './bills.component.scss'
})
export class BillsComponent implements OnInit {
  
  bills?: Bill[];

  searchOptions: [string, string][] = [];

  myControl = new FormControl('');

  filteredOptions?: Observable<[string, string][]>;
  
  myLocation?: string;

  public hasMoreContent: boolean = true;

  public isRequestingData: boolean = false;

  public page: Page = {
    index: "ObjectsByDate",
    ascending: false,
    pageSize: 25
  };

  constructor(private service: AppService, private router: Router, private route: ActivatedRoute) {}

  ngOnInit(): void
  {
    this.isRequestingData = true;

    // this.service.getLegislatorPageData().then(data => {
    //   this.bills = data.legislators;
    //   this.allLegislators = data.allLegislators;
    //   this.searchOptions = data.allLegislators.concat(states.map(s => ["STATE/" + s[1], s[0]]));
    //   this.myLocation = data.location;
    // }).finally(() => {
    //   this.isRequestingData = false;
    // });

    this.fetchData();

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

    // console.log("offsetHeight: " + el.offsetHeight + ", scrollTop: " + el.scrollTop + ", scrollHeight: " + el.scrollHeight);

    // TODO : If you want to use this code, you'll need to set lastScrollTop in all the data refresh events as well
    // Ignore scrolling upwards
    // if (el.scrollTop < this.lastScrollTop){
    //     return;
    // } else {
    //   this.lastScrollTop = el.scrollTop <= 0 ? 0 : el.scrollTop;
    // }

    // Trigger when scrolled to bottom
    if (el.offsetHeight + el.scrollTop >= (el.scrollHeight - 200) && this.bills != null) {
      const lastBill = this.bills[this.bills.length - 1];
      const sep = "~`~";

      if (this.page.index === "ObjectsByDate") {
        this.page.exclusiveStartKey = lastBill.id + sep + lastBill.introducedDate;
      } else if (this.page.index === "ObjectsByRating") {
        this.page.exclusiveStartKey = lastBill.id + sep + getBenefitToSocietyIssue(lastBill.interpretation!.issueStats)[1];
      // } else if (this.page.index === "ObjectsByLocation") {
      //   this.page.exclusiveStartKey = lastBill.id + sep + lastBill.terms[0].state;
      } else {
        console.log("Unknown page index: " + this.page.index);
        return
      }

      this.fetchData(false);
    }
  }

  onSelectAutocomplete(id: string) {
    // if (id.startsWith("STATE/")) {
    //   this.page.index = "ObjectsByLocation";
    //   this.page.sortKey = bioguideId.substring(6);
    //   this.page.ascending = true;
    //   this.fetchData();
    //   this.myControl.setValue("");
    // } else {
      this.router.navigate(['/bill', id]);
    // }
  }

  togglePage(index: "ObjectsByDate" | "ObjectsByRating" | "ObjectsByLocation") {
    this.page.ascending = (index == this.page.index) ? !this.page.ascending : false;
    this.page.index = index;
    this.page.exclusiveStartKey = undefined;
    this.hasMoreContent = true;

    if (index === "ObjectsByLocation") {
      this.page.sortKey = this.myLocation;
    } else {
      this.page.sortKey = undefined;
    }

    this.bills= [];

    this.fetchData();
  }

  fetchData(replace: boolean = true) {
    if (!this.hasMoreContent) return;

    this.isRequestingData = true;

    this.service.getBills(this.page).then(bills => {
      if (replace) {
        this.bills = bills;
      } else if (bills.length > 0 && this.bills?.findIndex(l => l.id == bills[0].id) == -1) {
        this.bills = this.bills.concat(bills);
      }

      if (bills.length == 0) {
        this.hasMoreContent = false;
      }
    }).finally(() => {
      this.isRequestingData = false;
    });
  }

  routeTo(bill: Bill)
  {
    document.getElementById(bill.id)?.classList.add("tran-div");
    this.router.navigate(['/bill', bill.id]);
  }

  descriptionForBill(bill: Bill): string
  {
    return bill.interpretation.issueStats.explanation.substring(0, 300) + "...";
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

  gradeForBill(bill: Bill): string
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

    return gradeForStats(bill.interpretation?.issueStats!);
  }

  colorForGrade(grade: string): string {
    return colorForGrade(grade);
  }

  subtitleForBill(bill: Bill): string
  {
    // return subtitleForStats(leg.interpretation?.issueStats!);

    // let term = bill.terms[bill.terms.length - 1];

    // return (term.chamber == "SENATE" ? "Senator" : "House") + " (" + convertStateCodeToName(term.state) + ")";

    let chamber = bill.type.toLowerCase().startsWith("s") ? "Senate" : "House";

    return "Sponsor: " + bill.sponsor.name + " (" + chamber + ")";
  }

}