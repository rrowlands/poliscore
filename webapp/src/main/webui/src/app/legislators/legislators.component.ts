import { AfterViewInit, Component, HostListener, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { AppService } from '../app.service';
import convertStateCodeToName, { Legislator, gradeForStats, issueKeyToLabel, colorForGrade, issueKeyToLabelSmall, subtitleForStats, Page, states, getBenefitToSocietyIssue, issueMap } from '../model';
import { CommonModule, KeyValuePipe, isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import {MatCardModule} from '@angular/material/card'; 
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import {MatButtonToggleModule} from '@angular/material/button-toggle'; 
import {MatAutocompleteModule} from '@angular/material/autocomplete';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Observable, map, startWith } from 'rxjs';
import { Title } from '@angular/platform-browser';
import { descriptionForLegislator, gradeForLegislator, subtitleForLegislator, upForReelection } from '../legislators';
import { HeaderComponent } from '../header/header.component';
import { ConfigService } from '../config.service';
import { MatMenuModule, MatMenuTrigger } from '@angular/material/menu';

@Component({
  selector: 'legislators',
  standalone: true,
  imports: [MatMenuModule, HeaderComponent, KeyValuePipe, CommonModule, RouterModule, MatCardModule, MatPaginatorModule, MatButtonToggleModule, MatAutocompleteModule, ReactiveFormsModule, MatButtonModule],
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
  
  myLocation?: string;

  public hasMoreContent: boolean = true;

  public isRequestingData: boolean = false;

  private lastDataFetchSequence = 0;

  issueMap = issueMap;

  public page: Page = {
    index: "ObjectsByLocation",
    ascending: true,
    pageSize: 25
  };

  constructor(public config: ConfigService, private service: AppService, private router: Router, private route: ActivatedRoute, @Inject(PLATFORM_ID) private _platformId: Object, private titleService: Title) {}

  ngOnInit(): void {
    this.route.fragment.subscribe(fragment => {
      if (fragment) {
        const params = new URLSearchParams(fragment);
        let routeIndex = params.get('index');
        let routeAscending = params.get('order');
        let routeLocation = params.get('location');
  
        if (routeIndex === "state" && routeLocation) {
          this.page.index = "ObjectsByLocation";
          this.page.ascending = true;
          this.myLocation = routeLocation.toLowerCase();
          this.titleService.setTitle(convertStateCodeToName(this.myLocation.toUpperCase()) + " Legislators - PoliScore: AI Political Rating Service");
          this.fetchLegislatorPageData(false, this.myLocation);
        } else if (routeIndex && routeAscending) {
          this.titleService.setTitle("Legislators - PoliScore: AI Political Rating Service");
          if (isPlatformBrowser(this._platformId)) { 
            this.isRequestingData = true;
            let routeParams = false;
    
            if ((routeIndex != null && routeIndex.length > 0)) {
              if (routeIndex === "byrating") {
                this.page.index = "ObjectsByRating";
              } else if (routeIndex === "byage") {
                this.page.index = "ObjectsByDate";
              } else if (routeIndex === "byimpact") {
                this.page.index = "ObjectsByImpact";
              } else if (routeIndex && routeIndex.length > 0) {
                this.page.index = "ObjectsByIssueImpact";
                this.page.sortKey = routeIndex!;
              }
    
              this.page.ascending = routeAscending === "ascending";
              this.fetchData();
              routeParams = true;
            }
    
            this.fetchLegislatorPageData(routeParams);
          }
        } else {
          // Default behavior if no fragment is present
          this.page.index = "ObjectsByLocation";
          this.page.ascending = true;
          this.titleService.setTitle("Legislators - PoliScore: AI Political Rating Service");
          this.fetchLegislatorPageData();
        }
      } else {
        // Default behavior if no fragment is present
        this.page.index = "ObjectsByLocation";
        this.page.ascending = true;
        this.titleService.setTitle("Legislators - PoliScore: AI Political Rating Service");
        this.fetchLegislatorPageData();
      }
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
    if (el.offsetHeight + el.scrollTop >= (el.scrollHeight - 200) && this.legs != null) {
      const lastLeg = this.legs[this.legs.length - 1];
      const sep = "~`~";

      if (this.page.index === "ObjectsByDate") {
        this.page.exclusiveStartKey = lastLeg.id + sep + lastLeg.birthday;
      } else if (this.page.index === "ObjectsByRating") {
        this.page.exclusiveStartKey = lastLeg.id + sep + getBenefitToSocietyIssue(lastLeg.interpretation!.issueStats)[1];
      } else if (this.page.index === "ObjectsByImpact") {
        this.page.exclusiveStartKey = lastLeg.id + sep + lastLeg.impact;
      } else if (this.page.index === "ObjectsByIssueImpact") {
        this.page.exclusiveStartKey = lastLeg.id + sep + lastLeg.impact;
      } else if (this.page.index === "ObjectsByLocation") {
        let lastTerm = lastLeg.terms[lastLeg.terms.length - 1];
        this.page.exclusiveStartKey = lastLeg.id + sep + lastTerm.state + (lastTerm.district == null ? "" : "/" + lastTerm.district);
      } else {
        console.log("Unknown page index: " + this.page.index);
        return
      }

      this.fetchData(false);
    }
  }

  onLegislatorSearchEnter(event: Event): void {
    // Prevent the default behavior of the Enter key in the autocomplete
    event.preventDefault();
  
    // Check if there's an active option and handle selection
    const activeOption = this.myControl.value; // Or your custom logic
    if (activeOption) {
      this.onSelectAutocomplete(activeOption); // Call your existing handler
    }
  }

  onSelectAutocomplete(id: string) {
    if (id.startsWith("STATE/")) {
      this.page.index = "ObjectsByLocation";
      // this.page.sortKey = bioguideId.substring(6);
      this.page.ascending = true;
      this.hasMoreContent = true;
      this.legs = [];
      this.page.exclusiveStartKey = undefined;
      this.myControl.setValue("");

      this.myLocation = id.substring(6);
      this.router.navigate(['/legislators/state/' + this.myLocation.toLowerCase()]);

      this.fetchLegislatorPageData(false, this.myLocation);
    } else {
      window.location.href = this.config.legislatorIdToAbsolutePath(id);
      // this.router.navigate(this.config.legislatorIdToAbsolutePath(id));
    }
  }

  togglePage(index: "ObjectsByDate" | "ObjectsByRating" | "ObjectsByLocation" | "ObjectsByImpact" | "ObjectsByIssueImpact",
              sortKey: string | undefined = undefined,
              menuTrigger: MatMenuTrigger | undefined = undefined,
              event: Event | undefined = undefined) {
    this.page.ascending = index === this.page.index && sortKey === this.page.sortKey ? !this.page.ascending : false;
    this.page.index = index;
    this.page.exclusiveStartKey = undefined;
    this.hasMoreContent = true;
    this.page.sortKey = sortKey;

    this.legs = [];

    this.titleService.setTitle("Legislators - PoliScore: AI Political Rating Service");

    let routeIndex = "";
    if (sortKey) {
      routeIndex = sortKey;
    } else if (this.page.index === "ObjectsByDate") {
      routeIndex = "byage";
    } else if (this.page.index === "ObjectsByRating") {
      routeIndex = "byrating";
    } else if (this.page.index === "ObjectsByImpact") {
      routeIndex = "byimpact";
    }

    if (this.page.index === "ObjectsByLocation") {
      this.page.ascending = true;
      this.router.navigate(['/legislators']);
      this.fetchLegislatorPageData();
    } else {
      this.router.navigate([], { fragment: `index=${routeIndex}&order=${this.page.ascending ? 'ascending' : 'descending'}` });
      this.fetchData();
    }

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

    this.service.getLegislators(this.page).then(legs => {
      if (fetchSeq != this.lastDataFetchSequence) return;

      if (replace) {
        this.legs = legs;
      } else if (legs.length > 0 && this.legs?.findIndex(l => l.id == legs[0].id) == -1) {
        this.legs = this.legs.concat(legs);
      }

      if (legs.length < (this.page.pageSize == null ? 25 : this.page.pageSize)) {
        this.hasMoreContent = false;
      }
    }).finally(() => {
      this.isRequestingData = false;
    });
  }

  fetchLegislatorPageData(routeParams: boolean = false, state: string | null = null) {
    this.service.getLegislatorPageData(state).then(data => {
      if (!routeParams) {
        this.legs = data.legislators;
      }

      this.allLegislators = data.allLegislators;
      this.searchOptions = data.allLegislators.concat(states.map(s => ["STATE/" + s[1], s[0]]));
      this.myLocation = data.location;

      let hasntChangedUrl = (this.router.url == "" || this.router.url == "/" || this.router.url == "/legislators");

      if (state == null && !routeParams && hasntChangedUrl) {
        this.router.navigate([], { fragment: `index=state&location=${this.myLocation.toLowerCase()}` });
      }
    }).finally(() => {
      if (!routeParams) {
        this.isRequestingData = false;
      }
    });
  }

  routeTo(leg: Legislator)
  {
    document.getElementById((leg.id ?? leg.legislatorId!))?.classList.add("tran-div");
    this.router.navigate(['/legislator/' + (leg.id ?? leg.legislatorId!).replace("LEG/us/congress", "")]);
  }


  colorForGrade(grade: any): any { return colorForGrade(grade); }
  gradeForLegislator(leg: Legislator): any { return gradeForLegislator(leg); }
  subtitleForLegislator(leg: Legislator) { return subtitleForLegislator(leg); }
  descriptionForLegislator(leg: Legislator) { return descriptionForLegislator(leg, isPlatformBrowser(this._platformId) && window.innerWidth < 480); }
  upForReelection(leg: Legislator): any { return upForReelection(leg); }
}
