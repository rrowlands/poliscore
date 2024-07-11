
import { Injectable } from '@angular/core';
import { HttpHeaders, HttpClient, HttpParams } from '@angular/common/http';
import { backendUrl } from './app.config';
import { Bill, Legislator, Page } from './model';
import { firstValueFrom } from 'rxjs/internal/firstValueFrom';

@Injectable()
export class AppService {
    
    constructor(private http: HttpClient) { }

    getLegislator(id: string): Promise<Legislator | undefined> {
        let params: HttpParams = new HttpParams();
        params = params.set("id", id);

        return firstValueFrom(this.http.get<Legislator>(backendUrl + "/getLegislator", { params: params }));
    }

    getLegislators(page: Page): Promise<Legislator[]> {
        let params: HttpParams = new HttpParams();
        
        if (page.index!= null) {
            params = params.set("index", page.index);
        }
        
        if (page.pageSize!= null) {
            params = params.set("pageSize", page.pageSize.toString());
        }

        if (page.exclusiveStartKey!= null) {
            params = params.set("exclusiveStartKey", page.exclusiveStartKey);
        }

        if (page.ascending!= null) {
            params = params.set("ascending", page.ascending.toString());
        }

        return firstValueFrom(this.http.get<Legislator[]>(backendUrl + "/getLegislators", { params: params }));
    }

    getBill(billId: string) {
        let params: HttpParams = new HttpParams();
        params = params.set("id", billId);

        return firstValueFrom(this.http.get<Bill>(backendUrl + "/getBill", { params: params }));
    }

}
