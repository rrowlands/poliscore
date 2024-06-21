import { Component, OnInit } from '@angular/core';
import { AppService } from '../app.service';
import { Legislator } from '../model';
import { HttpHeaders, HttpClient, HttpParams, HttpHandler, HttpClientModule } from '@angular/common/http';
import { CommonModule, KeyValuePipe } from '@angular/common';


@Component({
  selector: 'app-legislator',
  standalone: true,
  imports: [HttpClientModule, KeyValuePipe, CommonModule],
  providers: [AppService, HttpClient],
  templateUrl: './legislator.component.html',
  styleUrl: './legislator.component.scss'
})
export class LegislatorComponent implements OnInit {

  public leg?: Legislator;

  private bernieId: string = "LEG/us/congress/S000033";

  constructor(private service: AppService) {}

  ngOnInit(): void {
    this.service.getLegislator(this.bernieId).then(leg => {
      this.leg = leg;
    })
  }

}
