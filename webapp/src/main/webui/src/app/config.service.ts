import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

@Injectable({
  providedIn: 'root'
})
export class ConfigService {
  private congress: number;

  // constructor(@Inject(PLATFORM_ID) private platformId: Object) {
  //   if (isPlatformBrowser(this.platformId)) {
  //     const baseHref = document.querySelector('base')?.getAttribute('href') || '/';
  //     const congressMatch = baseHref.match(/^\/(\d+)\/$/); // Match "/118/" or similar
  //     this.congress = congressMatch ? parseInt(congressMatch[1], 10) : 118; // Default to 118
  //   } else {
  //     this.congress = 118;
  //   }
  // }

  constructor(@Inject(PLATFORM_ID) private platformId: Object) {
    if (isPlatformBrowser(this.platformId)) {
      const baseHref = document.querySelector('base')?.getAttribute('href') || '/';
      const yearMatch = baseHref.match(/^\/(\d{4})\/$/); // Match "/2024/" or similar
      const year = yearMatch ? parseInt(yearMatch[1], 10) : new Date().getFullYear(); // Default to current year

      // Calculate congressional session from the year
      this.congress = this.yearToCongress(year);
    } else {
      // Default to the current congressional session based on current year
      const currentYear = new Date().getFullYear();
      this.congress = this.yearToCongress(currentYear);
    }
  }

  public yearToCongressStr(year: string): string
  {
    // Congress started in 1789
    return (Math.floor((parseInt(year) - 1789) / 2) + 1).toString();
  }

  public yearToCongress(year: number): number
  {
    return Math.floor((year - 1789) / 2) + 1;
  }

  public billIdToPath(billId: string): string
  {
    return billId.replace('BIL/us/congress/' + this.congress + '/', '');
  }

  public pathToBillId(path: string): string
  {
    return "BIL/us/congress/" + this.congress + "/" + path;
  }

  public legislatorIdToPath(legislatorId: string): string
  {
    return legislatorId.replace('LEG/us/congress/' + this.congress + "/", '');
  }

  public pathToLegislatorId(path: string): string
  {
    return "LEG/us/congress/" + this.congress + "/" + path;
  }
}
