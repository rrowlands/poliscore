import { Routes } from '@angular/router';

import { AppComponent } from './app.component';
import { LegislatorComponent } from './legislator/legislator.component';
import { PoliscoreComponent } from './poliscore/poliscore.component';

export const routes: Routes = [
  { path: '', component: AppComponent },
  { path: 'poliscore', component: PoliscoreComponent },
  { path: 'legislator', component: LegislatorComponent },
];
