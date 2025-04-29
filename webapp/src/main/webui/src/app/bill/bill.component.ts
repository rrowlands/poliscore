import { Component, HostListener, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { AppService } from '../app.service';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Bill, BillInterpretation, colorForGrade, getBenefitToSocietyIssue, gradeForStats, issueKeyToLabel, issueKeyToLabelSmall } from '../model';
import { MatCardModule } from '@angular/material/card';
import { CommonModule, isPlatformBrowser, isPlatformServer } from '@angular/common';
import { HttpClient, HttpHandler } from '@angular/common/http';
import ChartDataLabels from 'chartjs-plugin-datalabels';
import { Chart, ChartConfiguration, BarController, CategoryScale, LinearScale, BarElement, Tooltip} from 'chart.js'
import { Meta, Title } from '@angular/platform-browser';
import { MatButtonModule } from '@angular/material/button';
import { ConfigService } from '../config.service';
import { HeaderComponent } from '../header/header.component';
import { DisclaimerComponent } from '../disclaimer/disclaimer.component';
import { MatTableModule } from '@angular/material/table';

Chart.register(BarController, CategoryScale, LinearScale, BarElement, ChartDataLabels, Tooltip);

@Component({
  selector: 'bill',
  standalone: true,
  imports: [MatTableModule, DisclaimerComponent, HeaderComponent, MatCardModule, CommonModule, CommonModule, RouterModule, MatButtonModule],
  providers: [AppService, HttpClient],
  templateUrl: './bill.component.html',
  styleUrl: './bill.component.scss'
})
export class BillComponent implements OnInit {

  public bill?: Bill;

  public billId?: string;

  public loading: boolean = true;

  public isSmallScreen = false;

  public barChartData: ChartConfiguration<'bar'>['data'] = {
    labels: [],
    datasets: []
  };

  public barChartOptions: ChartConfiguration<'bar'>['options'] = {
    indexAxis: "y",
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top',
      },
      title: {
        display: true,
        text: 'Chart.js Floating Bar Chart'
      },
      datalabels: {
        anchor: (ctx) => {
          return ctx.dataset.data[ctx.dataIndex] as number >= 0 ? "start" : "end";
        },
        align: 'center', // Align the text after the anchor point
        formatter: function (value, context) { // Show the label instead of the value
          return context?.chart?.data?.labels![context.dataIndex];
        },
        // font: { weight: "bold" }
      }
    },
    scales: {
      x: {
        min: -100,
        max: 100
      },
      // y: {ticks: {mirror: true, crossAlign: "center", align: "center", z: 1}}
      y: { ticks: { display: false } }
    }
  };

  constructor(public config: ConfigService, private meta: Meta, private service: AppService, private route: ActivatedRoute, private router: Router, @Inject(PLATFORM_ID) private _platformId: Object, private titleService: Title) { }

  @HostListener('window:resize', ['$event'])
  onResize() {
    // Check screen width on resize
    this.isSmallScreen = window.innerWidth < 600;
  }

  ngOnInit(): void {
    if (isPlatformBrowser(this._platformId))
      this.isSmallScreen = window.innerWidth < 600;

    this.billId = (this.route.snapshot.paramMap.get('id') as string);
    if (!this.billId.startsWith("BIL/us/congress")) {
      this.billId = this.config.pathToBillId(this.billId);
    }

    this.service.getBill(this.billId).then(bill => {
      this.bill = bill;

      if (bill)
        console.log(bill.pressInterps);

      if (bill == null)
        throw new Error("Backend did not return a bill for [" + this.billId + "]");

      if (bill.interpretation == null || bill.interpretation.longExplain == null || bill.interpretation.longExplain.length == 0
        || bill.interpretation.shortExplain == null || bill.interpretation.shortExplain.length == 0
        || bill.interpretation.issueStats == null || bill.interpretation.issueStats.stats == null || bill.interpretation.issueStats.stats['OverallBenefitToSociety'] == null) {
        throw new Error("Invalid interpretation for bill " + this.billId);
      }

      this.loading = false;
      this.updateMetaTags();
      this.buildBarChartData();
    });
  }

  updateMetaTags(): void {
    let billId = this.bill?.id ?? this.bill?.billId!;
    let billSession = parseInt(billId.split("/")[3]);
    let year = this.config.congressToYear(billSession);

    const pageTitle = this.bill!.name + " - Bill - PoliScore: AI Political Rating Service";
    const pageDescription = this.gradeForBill() + " (" + this.bill?.cosponsors.length + " cosponsors) - " + this.bill!.interpretation.shortExplain!.replace(/[\r\n]/g, '');
    const pageUrl = `https://poliscore.us` + this.config.billIdToAbsolutePath(billId);
    const imageUrl = 'https://poliscore.us/' + year + '/images/billonly.png';

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

  getCongressGovBillType() {
    if (this.bill?.type == "SJRES") {
      return "senate-joint-resolution";
    } else if (this.bill?.type == "HR") {
      return "house-bill";
    } else if (this.bill?.type == "HJRES") {
        return "house-joint-resolution";
    } else if (this.bill?.type == "S") {
        return "senate-bill";
    } else {
      return "";
    }
  }

  gradeForBill(): string { return this.gradeForInterp(this.bill?.interpretation!); }
  gradeForInterp(interp: BillInterpretation) { return gradeForStats(interp?.issueStats!); }

  colorForGrade(grade: string): string { return colorForGrade(this.gradeForBill()); }

  public getCosponsorSmall() {
    var plural = (this.bill!.cosponsors.length > 1 ? "s" : "");

    // if (this.bill!.cosponsors.length <= 2)
    //   return "Cosponsor" + plural + ": " + this.bill?.cosponsors.map(s => s.name).join(", ");
    // else
    return this.bill!.cosponsors.length + " cosponsor" + plural;
  }

  public getCosponsorLarge() {
    var plural = (this.bill!.cosponsors.length > 1 ? "s" : "");

    return "Cosponsor" + plural + ":\n\n" + this.bill?.cosponsors.map(s => "- <a href='" + this.config.legislatorIdToAbsolutePath(s.legislatorId) + "'>" + s.name.official_full + "</a>").join("\n");
  }

  getDisplayedColumns(): string[] {
    if (isPlatformBrowser(this._platformId) && window.innerWidth < 480) {
      return ['author', 'title', 'grade'];
    } else {
      return ['author', 'title', 'grade', "shortReport", "confidence"];
    }
  }

  isNonSafari() {
    return !(navigator.userAgent.includes('Safari') && !navigator.userAgent.includes('Chrome'));
  }

  openOrigin(url: string) {
    window.location.href = url;
  }

  async buildBarChartData() {
    let data: number[] = [];
    let labels: string[] = [];

    data.push(getBenefitToSocietyIssue(this.bill?.interpretation?.issueStats!)[1]);
    labels.push(getBenefitToSocietyIssue(this.bill?.interpretation?.issueStats!)[0]);

    let i = 0;
    for (const [key, value] of Object.entries(this.bill?.interpretation?.issueStats?.stats)
      .filter(kv => kv[0] != "OverallBenefitToSociety")
      .sort((a, b) => (b[1] as number) - (a[1] as number))) {
      data.push(value as number);
      labels.push(key);
    }
    
    if (isPlatformBrowser(this._platformId) && window.innerWidth < 480) {
      labels = labels.map(l => issueKeyToLabelSmall(l));
    } else {
      labels = labels.map(l => issueKeyToLabel(l));
    }

    this.barChartData.labels = labels;
    this.barChartData.datasets = [{
      data: data,
      label: "",
      backgroundColor: [
        'rgba(255, 99, 132, 0.2)',
        'rgba(255, 159, 64, 0.2)',
        'rgba(255, 205, 86, 0.2)',
        'rgba(75, 192, 192, 0.2)',
        'rgba(54, 162, 235, 0.2)',
        'rgba(153, 102, 255, 0.2)',
        '#FFF8DC',
        'rgba(255, 99, 132, 0.2)',
        'rgba(255, 159, 64, 0.2)'
      ],
      borderColor: [
        'rgb(255, 99, 132)',
        'rgb(255, 159, 64)',
        'rgb(255, 205, 86)',
        'rgb(75, 192, 192)',
        'rgb(54, 162, 235)',
        'rgb(153, 102, 255)',
        '#FFF8DC',
        'rgb(255, 99, 132)',
        'rgb(255, 159, 64)'
      ],
      borderWidth: 1
    }];

    if (isPlatformBrowser(this._platformId)) {
      window.setTimeout(() => {
        new Chart(
          document.getElementById('barChart') as any,
          {
            type: 'bar',
            data: this.barChartData,
            options: this.barChartOptions
          }
        );
      }, 10);
    }
  }

}
