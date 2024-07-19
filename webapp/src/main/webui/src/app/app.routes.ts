import { Routes } from '@angular/router';

import { AppComponent } from './app.component';
import { LegislatorComponent } from './legislator/legislator.component';
import { PoliscoreComponent } from './poliscore/poliscore.component';
import { LegislatorsComponent } from './legislators/legislators.component';
import { BillComponent } from './bill/bill.component';
import { BillsComponent } from './bills/bills.component';

export const routes: Routes = [
  { path: "", component: LegislatorsComponent, data: { animation: 'legislatorsPage' } },
  { path: 'legislator', component: LegislatorComponent, data: { animation: 'legislatorPage' } },
  { path: 'legislator/:id', component: LegislatorComponent, data: { animation: 'legislatorPage' } },
  { path: 'legislators', component: LegislatorsComponent, data: { animation: 'legislatorsPage' } },
  { path: 'bills', component: BillsComponent, data: { animation: 'billsPage' } },
  { path: 'bill', component: BillComponent, data: { animation: 'billPage' } },
  { path: 'bill/:id', component: BillComponent, data: { animation: 'billPage' } },
];
