import { Component, OnInit, ViewChild } from '@angular/core';
import { AppService } from '../app.service';
import { Legislator, issueKeyToLabel, getBenefitToSocietyIssue, IssueStats, gradeForStats, BillInteraction, colorForGrade, issueKeyToLabelSmall } from '../model';
import { HttpHeaders, HttpClient, HttpParams, HttpHandler, HttpClientModule } from '@angular/common/http';
import { CommonModule, DatePipe, KeyValuePipe } from '@angular/common';
import { BaseChartDirective } from 'ng2-charts';
import { Chart, ChartConfiguration, BarController, CategoryScale, LinearScale, BarElement} from 'chart.js'
import ChartDataLabels from 'chartjs-plugin-datalabels';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card'; 
import { MatTableModule } from '@angular/material/table';

/*
const floatingLabelsPlugin = {
  id: 'floating-labels',
  afterDatasetsDraw: function (chart: Chart) {
    console.log(chart);

    const ctx = chart.ctx;
    ctx.fillStyle = 'rgb(255, 255, 255)';
    ctx.font = '12px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'bottom';
    ctx.fillText("Test", chart.chartArea.width / 2, chart.chartArea.height / 2);
  }
};
*/

Chart.register(BarController, CategoryScale, LinearScale, BarElement, ChartDataLabels);

export const CHART_COLORS = {
  red: 'rgb(255, 99, 132)',
  orange: 'rgb(255, 159, 64)',
  yellow: 'rgb(255, 205, 86)',
  green: 'rgb(75, 192, 192)',
  blue: 'rgb(54, 162, 235)',
  purple: 'rgb(153, 102, 255)',
  grey: 'rgb(201, 203, 207)'
};

@Component({
  selector: 'app-legislator',
  standalone: true,
  imports: [HttpClientModule, KeyValuePipe, CommonModule, BaseChartDirective, MatCardModule, MatTableModule, DatePipe, RouterModule],
  providers: [AppService, HttpClient],
  templateUrl: './legislator.component.html',
  styleUrl: './legislator.component.scss'
})
export class LegislatorComponent implements OnInit {

  @ViewChild("barChart") barChart!: HTMLCanvasElement;

  displayedColumns: string[] = ['billName', 'billGrade', "association", "date"];
  public billData?: any;

  public leg?: Legislator;

  private legId?: string;

  public loading: boolean = true;

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

  constructor(private service: AppService, private route: ActivatedRoute, private router: Router) { }

  ngOnInit(): void {
    this.legId = this.route.snapshot.paramMap.get('id') as string;

    this.service.getLegislator(this.legId).then(leg => {
      this.leg = leg;
      this.loading = false;

      if (leg == null) { return; }

      this.billData = leg?.interactions
        ?.filter(i => i.issueStats != null)
        .map(i => ({
          billName: i.billName,
          billGrade: gradeForStats(i.issueStats),
          date: new Date(parseInt(i.date.split("-")[0]), parseInt(i.date.split("-")[1]) - 1, parseInt(i.date.split("-")[2])),
          association: this.describeAssociation(i),
          billId: i.billId
        }))
        .sort((a, b) => b.date.getTime() - a.date.getTime());

      this.buildBarChartData();
    });
  }

  gradeForLegislator(): string { return gradeForStats(this.leg?.interpretation?.issueStats!); }

  colorForGrade(grade: string): string { return colorForGrade(this.gradeForLegislator()); }

  getDisplayedColumns(): string[] {
    if (window.innerWidth < 480) {
      return ['billName', 'billGrade'];
    } else {
      return this.displayedColumns;
    }
  }

  routeToBill(id: string)
  {
    this.router.navigate(['/bill', id]);
  }

  describeAssociation(association: BillInteraction): string {
    if (association["@type"] == "LegislatorBillVote") {
      return "Voted " + (association.voteStatus?.toUpperCase() === "AYE" ? "For" : "Against");
    } else {
      return association["@type"].replace("LegislatorBill", "");
    }
  }

  async buildBarChartData() {
    let data: number[] = [];
    let labels: string[] = [];

    data.push(getBenefitToSocietyIssue(this.leg?.interpretation?.issueStats!)[1]);
    labels.push(getBenefitToSocietyIssue(this.leg?.interpretation?.issueStats!)[0]);

    let i = 0;
    for (const [key, value] of Object.entries(this.leg?.interpretation?.issueStats?.stats)
      .filter(kv => kv[0] != "OverallBenefitToSociety")
      .sort((a, b) => (b[1] as number) - (a[1] as number))) {
      data.push(value as number);
      labels.push(key);
    }

    if (window.innerWidth < 480) {
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

    await this.waitForImage(document.querySelector('img'));

    window.setTimeout(() => {
      new Chart(
        document.getElementById('barChart') as any,
        {
          type: 'bar',
          data: this.barChartData,
          options: this.barChartOptions
          // plugins: [ChartDataLabels, floatingLabelsPlugin],
        }
      );
    }, 10);
  }

  waitForImage(imgElem: any) {
    return new Promise(res => {
      if (imgElem.complete) {
        return res(null);
      }
      imgElem.onload = () => res(null);
      imgElem.onerror = () => res(null);
    });
  }
}
