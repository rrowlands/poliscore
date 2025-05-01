import { Component, Inject, PLATFORM_ID } from '@angular/core';
import { ChildrenOutletContexts, RouterOutlet } from '@angular/router';
import { slideInAnimation } from './animations';
import { Router, NavigationEnd } from '@angular/router';
import { isPlatformBrowser } from '@angular/common';

declare let gtag: Function;

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  providers: [],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  animations: [
    slideInAnimation
  ]
})
export class AppComponent {
  constructor(public router: Router, private contexts: ChildrenOutletContexts, @Inject(PLATFORM_ID) private _platformId: Object){
    if (isPlatformBrowser(this._platformId)) {
      this.router.events.subscribe(event => {
        if(event instanceof NavigationEnd){
            gtag('config', 'G-7DY6Y1SM6W',
                  {
                    'page_path': event.urlAfterRedirects
                  }
                  );
          }
      });
     }
  }

  getRouteAnimationData() {
    return this.contexts.getContext('primary')?.route?.snapshot?.data?.['animation'];
  }
}
