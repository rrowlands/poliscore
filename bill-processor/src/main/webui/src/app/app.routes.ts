import { Routes } from '@angular/router';

import { AppComponent } from './app.component';
import { LegislatorComponent } from './legislator/legislator.component';
import { PoliscoreComponent } from './poliscore/poliscore.component';

export const routes: Routes = [
  { path: "", component: PoliscoreComponent, data: { animation: 'poliscorePage' } },
  { path: 'legislator', component: LegislatorComponent, data: { animation: 'legislatorPage' } },
];
