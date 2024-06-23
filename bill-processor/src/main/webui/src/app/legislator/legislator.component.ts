import { Component, OnInit } from '@angular/core';
import { AppService } from '../app.service';
import { Legislator, issueKeyToLabel } from '../model';
import { HttpHeaders, HttpClient, HttpParams, HttpHandler, HttpClientModule } from '@angular/common/http';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { BaseChartDirective } from 'ng2-charts';
import { Chart, ChartConfiguration, BarController, CategoryScale, LinearScale, BarElement} from 'chart.js'
import ChartDataLabels from 'chartjs-plugin-datalabels';
import { ActivatedRoute } from '@angular/router';

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
  imports: [HttpClientModule, KeyValuePipe, CommonModule, BaseChartDirective],
  providers: [AppService, HttpClient],
  templateUrl: './legislator.component.html',
  styleUrl: './legislator.component.scss'
})
export class LegislatorComponent implements OnInit {

  public leg?: Legislator;

  private legId?: string;

  public barChartData: ChartConfiguration<'bar'>['data'] = {
    labels: [ '2006', '2007', '2008', '2009', '2010', '2011', '2012' ],
    datasets: [
      { data: [ 65, 59, 80, 81, 56, 55, 40 ], label: 'Series A' }
    ]
  };

  public barChartOptions: ChartConfiguration<'bar'>['options'] = {
    indexAxis: "y",
    plugins: {
      legend: {
        position: 'top',
      },
      title: {
        display: true,
        text: 'Chart.js Floating Bar Chart'
      },
      datalabels: {
        anchor: 'start', // Anchor the labels to the start of the datapoint
        align: 'center', // Align the text after the anchor point
        formatter: function(value, context) { // Show the label instead of the value
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
      y: {ticks: {display: false}}
    }
  };

  constructor(private service: AppService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.legId = this.route.snapshot.paramMap.get('id') as string;

    this.service.getLegislator(this.legId).then(leg => {
      this.leg = leg;

      this.buildBarChartData();
    });
  }

  buildBarChartData(): void {
    let data: number[] = [];
    let labels: string[]= [];

    data.push(Object.entries(this.leg?.interpretation?.issueStats?.stats).filter(kv => kv[0] === "OverallBenefitToSociety")[0][1] as number);
    labels.push(Object.entries(this.leg?.interpretation?.issueStats?.stats).filter(kv => kv[0] === "OverallBenefitToSociety")[0][0]);

    let i = 0;
    for (const [key, value] of Object.entries(this.leg?.interpretation?.issueStats?.stats)
      .filter(kv => kv[0] != "OverallBenefitToSociety")
      .sort((a,b) => (b[1] as number) - (a[1] as number))) {
      data.push(value as number);
      labels.push(key);
    }
    labels = labels.map(l => issueKeyToLabel(l));

    this.barChartData.labels = labels;
    this.barChartData.datasets = [ {
      data: data,
      label: "",
      backgroundColor: [
        'rgba(255, 99, 132, 0.2)',
        'rgba(255, 159, 64, 0.2)',
        'rgba(255, 205, 86, 0.2)',
        'rgba(75, 192, 192, 0.2)',
        'rgba(54, 162, 235, 0.2)',
        'rgba(153, 102, 255, 0.2)',
        'rgba(201, 203, 207, 0.2)',
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
        'rgb(201, 203, 207)',
        'rgb(255, 99, 132)',
        'rgb(255, 159, 64)'
      ],
      borderWidth: 1
    } ];

    /*
    const DATA_COUNT = 7;
    const NUMBER_CFG = {count: DATA_COUNT, min: -100, max: 100};

    const data2 = {
      labels: ["1","2","3"],
      datasets: [
        {
          label: 'Dataset 1',
          data: [-1,2,3],
          backgroundColor: CHART_COLORS.red,
        }
      ]
    };
    */

    new Chart(
      document.getElementById('barChart') as any,
      {
        type: 'bar',
        data: this.barChartData,
        options: this.barChartOptions
      }
    );
  }
}
