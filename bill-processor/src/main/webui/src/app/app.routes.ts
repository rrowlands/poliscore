import { Routes } from '@angular/router';

import { AppComponent } from './app.component';
import { LegislatorComponent } from './legislator/legislator.component';
import { PoliscoreComponent } from './poliscore/poliscore.component';
import { LegislatorsComponent } from './legislators/legislators.component';

export const routes: Routes = [
  { path: "", component: PoliscoreComponent, data: { animation: 'poliscorePage' } },
  { path: 'legislator', component: LegislatorComponent, data: { animation: 'legislatorPage' } },
  { path: 'legislator/:id', component: LegislatorComponent, data: { animation: 'legislatorPage' } },
  { path: 'legislators', component: LegislatorsComponent, data: { animation: 'legislatorsPage' } }
];
