import { Component, Inject, PLATFORM_ID, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Chart, ChartConfiguration, BarController, CategoryScale, LinearScale, BarElement, Tooltip} from 'chart.js'
import ChartDataLabels from 'chartjs-plugin-datalabels';
import { AppService } from '../app.service';
import { Title } from '@angular/platform-browser';
import { getBenefitToSocietyIssue, issueKeyToLabel, issueKeyToLabelSmall, SessionStats } from '../model';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { MatCardModule } from '@angular/material/card'; 
import { HttpClient } from '@angular/common/http';

Chart.register(BarController, CategoryScale, LinearScale, BarElement, ChartDataLabels, Tooltip);

@Component({
  selector: 'sessionstats',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  providers: [AppService, HttpClient],
  templateUrl: './sessionstats.component.html',
  styleUrl: './sessionstats.component.scss'
})
export class SessionStatsComponent {

  @ViewChild("barChart") barChart!: HTMLCanvasElement;

  public party: "REPUBLICAN" | "DEMOCRAT" | "INDEPENDENT" = "REPUBLICAN";

  public stats?: SessionStats;

  public loading: boolean = true;

  public isRequestingData: boolean = false;

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

  constructor(private service: AppService, private route: ActivatedRoute, private router: Router, @Inject(PLATFORM_ID) private _platformId: Object, private titleService: Title) { }

  ngOnInit(): void {
    this.service.getSessionStats().then(stats => {
      this.stats = stats;
      this.loading = false;

      if (stats == null) { return; }

      this.titleService.setTitle(stats?.session + "th Congress Stats - PoliScore: non-partisan political rating service");

      this.buildBarChartData();
    });
  }

  async buildBarChartData() {
    if (this.party == null || this.stats == null) return;

    console.log(this.stats);

    let partyStats = this.stats.partyStats[this.party].stats;

    let data: number[] = [];
    let labels: string[] = [];

    data.push(getBenefitToSocietyIssue(partyStats)[1]);
    labels.push(getBenefitToSocietyIssue(partyStats)[0]);

    let i = 0;
    for (const [key, value] of Object.entries(partyStats.stats)
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

    // this.barChart.style.maxWidth = 

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
      // await this.waitForImage(document.querySelector('img'));
      // window.setTimeout(() => { this.renderBarChart(); }, 10);
      this.renderBarChart();
    }
  }

  renderBarChart() {
    new Chart(
      (this.barChart as any).nativeElement,
      {
        type: 'bar',
        data: this.barChartData,
        options: this.barChartOptions
        // plugins: [ChartDataLabels, floatingLabelsPlugin],
      }
    );
  }
}
