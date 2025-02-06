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
    this.congress = this.yearToCongress(this.getYear());
  }

  public getYear(): number
  {
    // if (isPlatformBrowser(this.platformId)) {
    //   const baseHref = document.querySelector('base')?.getAttribute('href') || '/';
    //   const yearMatch = baseHref.match(/^\/(\d{4})\/$/); // Match "/2024/" or similar
    //   return yearMatch ? parseInt(yearMatch[1], 10) : new Date().getFullYear(); // Default to current year
    // } else {
    //   return new Date().getFullYear();
    // }

    return 2026;
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

  public congressToYear(congress: number): number
  {
    return (congress - 1) * 2 + 1789 + 1;
  }

  // public billIdToPath(billId: string): string
  // {
  //   return billId.replace('BIL/us/congress/' + this.congress + '/', '');
  // }

  public billIdToAbsolutePath(billId: string): string
  {
    var billSession = parseInt(billId.split("/")[3]);
    var year = this.congressToYear(billSession);

    return "/" + year + "/bill/" +  billId.replace('BIL/us/congress/' + billSession + '/', '');
  }

  public pathToBillId(path: string): string
  {
    return "BIL/us/congress/" + this.congress + "/" + path;
  }

  // public legislatorIdToPath(legislatorId: string): string
  // {
  //   return legislatorId.replace('LEG/us/congress/' + this.congress + "/", '');
  // }

  public legislatorIdToAbsolutePath(legislatorId: string): string
  {
    var session = parseInt(legislatorId.split("/")[3]);
    var year = this.congressToYear(session);
    var bioguideId = legislatorId.split("/")[4];

    return "/" + year + "/legislator/" + bioguideId;
  }

  public pathToLegislatorId(path: string): string
  {
    return "LEG/us/congress/" + this.congress + "/" + path;
  }
}
