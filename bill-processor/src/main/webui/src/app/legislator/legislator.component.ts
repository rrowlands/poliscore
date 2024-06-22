import { Component, OnInit } from '@angular/core';
import { AppService } from '../app.service';
import { Legislator } from '../model';
import { HttpHeaders, HttpClient, HttpParams, HttpHandler, HttpClientModule } from '@angular/common/http';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { BaseChartDirective } from 'ng2-charts';
import { Chart, ChartConfiguration, BarController, CategoryScale, LinearScale, BarElement} from 'chart.js'

Chart.register(BarController, CategoryScale, LinearScale, BarElement);

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

  private bernieId: string = "LEG/us/congress/S000033";

  public barChartData: ChartConfiguration<'bar'>['data'] = {
    labels: [ '2006', '2007', '2008', '2009', '2010', '2011', '2012' ],
    datasets: [
      { data: [ 65, 59, 80, 81, 56, 55, 40 ], label: 'Series A' }
    ]
  };

  public barChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: false,
  };

  constructor(private service: AppService) {}

  ngOnInit(): void {
    this.service.getLegislator(this.bernieId).then(leg => {
      this.leg = leg;

      this.buildBarChartData();
    })
  }

  buildBarChartData(): void {
    let data: number[] = [];
    let labels: string[]= [];

    for (const [key, value] of Object.entries(this.leg?.interpretation?.issueStats?.stats)) {
      console.log(`${key}: ${value}`);

      data.push(value as number);
      labels.push(key);
    }

    this.barChartData.labels = labels;
    this.barChartData.datasets = [ { data: data, label: "" } ];


    new Chart(
      document.getElementById('barChart') as any,
      {
        type: 'bar',
        data: this.barChartData
      }
    );
  }

}
