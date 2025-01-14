import { Component, HostListener, OnInit } from '@angular/core';
import { AppService } from '../app.service';
import convertStateCodeToName, { Legislator, gradeForStats, issueKeyToLabel, colorForGrade, issueKeyToLabelSmall, subtitleForStats, Page, states, getBenefitToSocietyIssue, Bill } from '../model';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import {MatCardModule} from '@angular/material/card'; 
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import {MatButtonToggleModule} from '@angular/material/button-toggle'; 
import {MatAutocompleteModule} from '@angular/material/autocomplete';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Observable, debounceTime, distinctUntilChanged, map, startWith, switchMap } from 'rxjs';
import { BillComponent } from '../bill/bill.component';
import { Title } from '@angular/platform-browser';
import { descriptionForBill, gradeForBill, subtitleForBill } from '../bills';
import { ConfigService } from '../config.service';
import { HeaderComponent } from '../header/header.component';

@Component({
  selector: 'bills',
  standalone: true,
  imports: [HeaderComponent, KeyValuePipe, CommonModule, RouterModule, MatCardModule, MatPaginatorModule, MatButtonToggleModule, MatAutocompleteModule, ReactiveFormsModule, MatButtonModule],
  providers: [AppService, HttpClient],
  templateUrl: './bills.component.html',
  styleUrl: './bills.component.scss'
})
export class BillsComponent implements OnInit {
  
  bills?: Bill[];

  searchOptions: [string, string][] = [];

  myControl = new FormControl('');

  filteredOptions?: Observable<[string, string][]>;

  public hasMoreContent: boolean = true;

  public isRequestingData: boolean = false;

  private lastDataFetchSequence: number = 0;

  public page: Page = {
    index: "ObjectsByImportance",
    ascending: false,
    pageSize: 25
  };

  constructor(public config: ConfigService, private service: AppService, private router: Router, private route: ActivatedRoute, private titleService: Title) {}

  ngOnInit(): void
  {
    this.titleService.setTitle("Bills - PoliScore: AI Political Rating Service");

    let routeIndex = this.route.snapshot.paramMap.get('index') as string;
    let routeAscending = this.route.snapshot.paramMap.get('ascending') as string;
    if ( (routeIndex === "byimportance" || routeIndex === "byrating" || routeIndex === "bydate") && routeAscending != null) {
      if (routeIndex === "byimportance") {
        this.page.index = "ObjectsByImportance";
      } else if (routeIndex === "byrating") {
        this.page.index = "ObjectsByRating";
      } else if (routeIndex === "bydate") {
        this.page.index = "ObjectsByDate";
      }

      this.page.ascending = routeAscending == "ascending";
    }

    this.fetchData();

    this.filteredOptions = this.myControl.valueChanges
        .pipe(
          startWith(''),
          debounceTime(400),
          distinctUntilChanged(),
          switchMap(val => {
            return this.filter(val || '')
          })       
        );
  }

  private filter(value: string): Observable<[string, string][]> {
    const filterValue = value.toLowerCase();

    // return this.searchOptions.filter(leg => leg[1].toLowerCase().includes(filterValue.toLowerCase()));

    // return this.service.queryBills(filterValue).then(data => {
    //   this.bills = data.bills;
    // });

    return this.service.queryBills(filterValue);
  }

  @HostListener('window:scroll', ['$event'])
  onScroll(e: any) {
    if (this.isRequestingData) return;

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
      } else if (this.page.index === "ObjectsByImportance") {
         this.page.exclusiveStartKey = lastBill.id + sep + lastBill.importance;
      } else {
        console.log("Unknown page index: " + this.page.index);
        return
      }

      this.fetchData(false);
    }
  }

  onSelectAutocomplete(id: string) {
    if (id.startsWith("~ti~")) {
      this.page.index = id as any;
      this.page.sortKey = undefined;
      this.page.ascending = true;
      this.fetchData();
      this.myControl.setValue("");
    } else {
      this.router.navigate(['/bill/' + this.config.billIdToPath(id)]);
    }
  }

  togglePage(index: "ObjectsByDate" | "ObjectsByRating" | "ObjectsByImportance") {
    this.page.ascending = (index == this.page.index) ? !this.page.ascending : false;
    this.page.index = index;
    this.page.exclusiveStartKey = undefined;
    this.hasMoreContent = true;
    this.page.sortKey = undefined;

    this.bills= [];

    let routeIndex = "byimportance";
    if (this.page.index === "ObjectsByDate") {
      routeIndex = "bydate";
    } else if (this.page.index === "ObjectsByRating") {
      routeIndex = "byrating";
    } else if (this.page.index === "ObjectsByImportance") {
      routeIndex = "byimportance";
    }

    this.router.navigate(['/bills', routeIndex, this.page.ascending? "ascending" : "descending"]);

    this.fetchData();
  }

  fetchData(replace: boolean = true) {
    if (!this.hasMoreContent) return;

    this.isRequestingData = true;
    
    let fetchSeq = ++this.lastDataFetchSequence;

    this.service.getBills(this.page).then(bills => {
      if (fetchSeq != this.lastDataFetchSequence) return;

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

  gradeForBill(bill: Bill): string { return gradeForBill(bill); }
  subtitleForBill(bill: Bill) { return subtitleForBill(bill); }
  descriptionForBill(bill: Bill) { return descriptionForBill(bill); }
  colorForGrade(grade: string): string { return colorForGrade(grade); }
}
