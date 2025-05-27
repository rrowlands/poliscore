import { Route, Routes, UrlMatchResult, UrlSegment, UrlSegmentGroup } from '@angular/router'; // UrlMatchResult, UrlSegment, UrlSegmentGroup already present

import { LegislatorComponent } from './legislator/legislator.component';
import { AboutComponent } from './about/about.component';
import { LegislatorsComponent } from './legislators/legislators.component';
import { BillComponent } from './bill/bill.component';
import { BillsComponent } from './bills/bills.component';
import { SessionStatsComponent } from './sessionstats/sessionstats.component';

function idPathMatcher(path: string, offset: number = 0) { // Added offset
  let p = path;
  return (segments: UrlSegment[], 
    group: UrlSegmentGroup, 
    route: Route): UrlMatchResult | null => { // Added explicit null return type
      if (segments.length > offset && segments[offset].path == p) { // Use offset
        return {
          consumed: segments, // Consumed all segments including namespace
          posParams: {
            // If namespace is the first segment, it's already captured by Angular's :namespace
            // So, the ID for the component should be segments after 'legislator' or 'bill' path part
            id: new UrlSegment(segments.slice(offset + 1).join("/"), {})
          }
        };
      }
      return null;
  };
}

// function legislatorPathMatcher(path: string) { // Kept commented as per original
//   let p = path;
  
//   return (segments: UrlSegment[], 
//     group: UrlSegmentGroup, 
//     route: Route) => {
//       if (segments.length > 0 && segments[0].path == p) {
//         return {
//           consumed: segments,
//           posParams: {
//             id: new UrlSegment(segments.slice(1,2).join("/"), {}),
//             index: new UrlSegment(segments.slice(2,3).join("/"), {}),
//             ascending: new UrlSegment(segments.slice(3,4).join("/"), {})
//           }
//         };
//       }
      
//       return null;
//   };
// }

export const routes: Routes = [
  { path: "", redirectTo: 'us/congress/legislators', pathMatch: 'full' }, // Default redirect
  {
    path: ':namespace', // Top-level namespace parameter
    children: [
      { path: "", redirectTo: 'legislators', pathMatch: 'full' }, // Default for namespace
      { path: 'legislators', component: LegislatorsComponent, data: { animation: 'legislatorsPage' } },
      { path: 'legislators/:index/:ascending', component: LegislatorsComponent, data: { animation: 'legislatorsPage' } },
      { matcher: idPathMatcher('legislator', 1), component: LegislatorComponent, data: { animation: 'legislatorPage' } }, // Offset 1 due to :namespace
      
      { path: 'bills', component: BillsComponent, data: { animation: 'billsPage' } },
      { path: 'bills/:index/:ascending', component: BillsComponent, data: { animation: 'billsPage' } },
      { matcher: idPathMatcher("bill", 1), component: BillComponent, data: { animation: 'billPage' } }, // Offset 1
      
      { path: 'session', component: SessionStatsComponent, data: { animation: 'sessionStatsPage' } }, // Replaces '/congress'
      { path: 'session/:party', component: SessionStatsComponent, data: { animation: 'sessionStatsPage' } },
      { path: 'session/:party/:sort', component: SessionStatsComponent, data: { animation: 'sessionStatsPage' } },
    ]
  },
  { path: 'about', component: AboutComponent, title: "About - PoliScore: AI Political Rating Service", data: { animation: 'about' } }
  // Add other top-level routes here if any (e.g., a global landing page if not redirecting)
];
