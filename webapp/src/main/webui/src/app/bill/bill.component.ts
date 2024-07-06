import { Component, OnInit } from '@angular/core';
import { AppService } from '../app.service';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { Bill, colorForGrade, getBenefitToSocietyIssue, gradeForStats, issueKeyToLabel, issueKeyToLabelSmall } from '../model';
import { MatCardModule } from '@angular/material/card';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpClientModule, HttpHandler } from '@angular/common/http';
import { Chart, ChartConfiguration } from 'chart.js';

@Component({
  selector: 'app-bill',
  standalone: true,
  imports: [MatCardModule, CommonModule, HttpClientModule, CommonModule, RouterModule],
  providers: [AppService, HttpClient],
  templateUrl: './bill.component.html',
  styleUrl: './bill.component.scss'
})
export class BillComponent implements OnInit {

  public bill?: Bill;

  public billId?: string;

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

  constructor(private service: AppService, private route: ActivatedRoute) { }

  ngOnInit(): void {
    this.billId = this.route.snapshot.paramMap.get('id') as string;

    this.service.getBill(this.billId).then(bill => {
      this.bill = bill;
      this.loading = false;
      this.buildBarChartData();
    });
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

  gradeForBill(): string { return gradeForStats(this.bill?.interpretation?.issueStats!); }

  colorForGrade(grade: string): string { return colorForGrade(this.gradeForBill()); }

  public getCosponsors() {
    return this.bill?.cosponsors.map(s => s.name).join(", ");
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
    
    if (window.innerWidth < 480) {
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
