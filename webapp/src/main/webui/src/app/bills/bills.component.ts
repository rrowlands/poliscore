import { Component, HostListener, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { AppService } from '../app.service';
import convertStateCodeToName, { Legislator, gradeForStats, issueKeyToLabel, colorForGrade, issueKeyToLabelSmall, subtitleForStats, Page, states, getBenefitToSocietyIssue, Bill, issueMap } from '../model';
import { CommonModule, isPlatformBrowser, KeyValuePipe } from '@angular/common';
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
import { Meta, Title } from '@angular/platform-browser';
import { descriptionForBill, gradeForBill, subtitleForBill } from '../bills';
import { ConfigService } from '../config.service';
import { HeaderComponent } from '../header/header.component';
import { MatMenuModule, MatMenuTrigger } from '@angular/material/menu';

@Component({
  selector: 'bills',
  standalone: true,
  imports: [MatMenuModule, HeaderComponent, KeyValuePipe, CommonModule, RouterModule, MatCardModule, MatPaginatorModule, MatButtonToggleModule, MatAutocompleteModule, ReactiveFormsModule, MatButtonModule],
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

  issueMap = issueMap;

  public page: Page = {
    index: "ObjectsByImpact",
    ascending: false,
    pageSize: 25
  };

  constructor(public config: ConfigService, private meta: Meta, @Inject(PLATFORM_ID) private _platformId: Object, private service: AppService, private router: Router, private route: ActivatedRoute, private titleService: Title) {}

  ngOnInit(): void
  {
    this.updateMetaTags();

    // We don't want to cache any of the returned bill data because it will display the wrong data for a second if they load
    // the page with query parameters
    if (isPlatformBrowser(this._platformId)) {
      this.route.fragment.subscribe(fragment => {
        if (fragment) {
          const params = new URLSearchParams(fragment);
          let routeIndex = params.get('index');
          let routeAscending = params.get('ascending');

          if ((routeIndex != null && routeIndex.length > 0)) {
            if (routeIndex === "byrating") {
              this.page.index = "ObjectsByRating";
            } else if (routeIndex === "bydate") {
              this.page.index = "ObjectsByDate";
            } else if (routeIndex === "byimpact") {
              this.page.index = "ObjectsByImpact";
            } else if (routeIndex && routeIndex.length > 0) {
              this.page.index = "ObjectsByIssueImpact";
              this.page.sortKey = routeIndex!;
            }

            this.page.ascending = routeAscending === "ascending";
          }

          this.fetchData();
        } else {
          this.fetchData();
        }
      });

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
  }

  private filter(value: string): Observable<[string, string][]> {
    const filterValue = value.toLowerCase();

    // return this.searchOptions.filter(leg => leg[1].toLowerCase().includes(filterValue.toLowerCase()));

    // return this.service.queryBills(filterValue).then(data => {
    //   this.bills = data.bills;
    // });

    return this.service.queryBills(filterValue);
  }

  updateMetaTags(): void {
    let year = this.config.getYear();

    let pageTitle = "Bills - PoliScore: AI Political Rating Service";
    const pageDescription = this.config.appDescription();
    const pageUrl = "https://poliscore.us/" + year + "/bills";
    const imageUrl = 'https://poliscore.us/' + year + '/images/poliscore-word-whitebg.png';

    this.titleService.setTitle(pageTitle);
    
    this.meta.updateTag({ property: 'og:title', content: pageTitle });
    this.meta.updateTag({ property: 'og:description', content: pageDescription });
    this.meta.updateTag({ property: 'og:url', content: pageUrl });
    this.meta.updateTag({ property: 'og:image', content: imageUrl });
    this.meta.updateTag({ property: 'og:type', content: 'website' });

    // Twitter meta tags (optional)
    this.meta.updateTag({ name: 'twitter:card', content: 'summary_large_image' });
    this.meta.updateTag({ name: 'twitter:title', content: pageTitle });
    this.meta.updateTag({ name: 'twitter:description', content: pageDescription });
    this.meta.updateTag({ name: 'twitter:image', content: imageUrl });
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
      const lastBillId = (lastBill.id ?? lastBill.billId);
      const sep = "~`~";

      if (this.page.index === "ObjectsByDate") {
        this.page.exclusiveStartKey = lastBillId + sep + lastBill.introducedDate;
      } else if (this.page.index === "ObjectsByRating") {
        this.page.exclusiveStartKey = lastBillId + sep + getBenefitToSocietyIssue(lastBill.interpretation!.issueStats)[1];
      } else if (this.page.index === "ObjectsByImpact") {
         this.page.exclusiveStartKey = lastBillId + sep + lastBill.impact;
      } else if (this.page.index === "ObjectsByIssueImpact") {
        this.page.exclusiveStartKey = lastBillId + sep + lastBill.impact;
      } else {
        console.log("Unknown page index: " + this.page.index);
        return
      }

      this.fetchData(false);
    }
  }

  onBillSearchEnter(event: Event): void {
    // Prevent the default behavior of the Enter key in the autocomplete
    event.preventDefault();
  
    // Check if there's an active option and handle selection
    const activeOption = this.myControl.value; // Or your custom logic
    if (activeOption) {
      this.onSelectAutocomplete(activeOption); // Call your existing handler
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
      window.location.href = this.config.billIdToAbsolutePath(id);
    }
  }

  togglePage(index: "ObjectsByDate" | "ObjectsByRating" | "ObjectsByImpact" | "ObjectsByIssueImpact",
                sortKey: string | undefined = undefined,
                menuTrigger: MatMenuTrigger | undefined = undefined,
                event: Event | undefined = undefined) {
    this.page.ascending = index === this.page.index && sortKey === this.page.sortKey ? !this.page.ascending : false;
    this.page.index = index;
    this.page.exclusiveStartKey = undefined;
    this.hasMoreContent = true;
    this.page.sortKey = sortKey;

    this.bills= [];

    let routeIndex = "";
    if (sortKey) {
      routeIndex = sortKey;
    } else if (this.page.index === "ObjectsByDate") {
      routeIndex = "bydate";
    } else if (this.page.index === "ObjectsByRating") {
      routeIndex = "byrating";
    } else if (this.page.index === "ObjectsByImpact") {
      routeIndex = "byimpact";
    }

    // this.router.navigate(['/bills', routeIndex, this.page.ascending? "ascending" : "descending"]);
    this.router.navigate([], { fragment: `index=${routeIndex}&ascending=${this.page.ascending ? 'ascending' : 'descending'}` });

    // this.fetchData();

    if (event && menuTrigger) {
      event.stopPropagation();
      const overlayDiv = document.querySelector('.cdk-overlay-connected-position-bounding-box');
      if (overlayDiv) overlayDiv.classList.add('closing');
      setTimeout(() => {
        if (overlayDiv) overlayDiv.classList.remove('closing');
        if (overlayDiv) overlayDiv.classList.add('hidden');
        menuTrigger!.closeMenu();
        setTimeout(() => {
          if (overlayDiv) overlayDiv.classList.remove('hidden');
        }, 500);
      }, 300);
    }
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
