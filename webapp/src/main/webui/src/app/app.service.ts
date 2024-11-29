
import { Injectable } from '@angular/core';
import { HttpHeaders, HttpClient, HttpParams } from '@angular/common/http';
import { backendUrl } from './app.config';
import { Bill, BillInteraction, Legislator, LegislatorPageData, Page, SessionStats } from './model';
import { firstValueFrom } from 'rxjs/internal/firstValueFrom';
import { Observable } from 'rxjs';

export class AppDataPage {
    data!: any[];
    exclusiveStartKey: any;
    hasMoreData!: boolean;
}

@Injectable()
export class AppService {
    
    constructor(private http: HttpClient) { }

    getSessionStats(): Promise<SessionStats | undefined> {
        let params: HttpParams = new HttpParams();

        return firstValueFrom(this.http.get<SessionStats>(backendUrl + "/getSessionStats", { params: params }));
    }

    getLegislator(id: string, page: Page): Promise<Legislator | undefined> {
        let params: HttpParams = new HttpParams();
        params = params.set("id", id);

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

        if (page.sortKey!= null) {
            params = params.set("sortKey", page.sortKey);
        }

        return firstValueFrom(this.http.get<Legislator>(backendUrl + "/getLegislator", { params: params }));
    }

    getLegislatorInteractions(id: string, page: Page): Promise<AppDataPage> {
        let params: HttpParams = new HttpParams();
        params = params.set("id", id);

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

        if (page.sortKey!= null) {
            params = params.set("sortKey", page.sortKey);
        }

        return firstValueFrom(this.http.get<AppDataPage>(backendUrl + "/getLegislatorInteractions", { params: params }));
    }

    getBills(page: Page): Promise<Bill[]> {
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

        if (page.sortKey!= null) {
            params = params.set("sortKey", page.sortKey);
        }

        return firstValueFrom(this.http.get<Bill[]>(backendUrl + "/getBills", { params: params }));
    }

    queryBills(text: string): Observable<[string,string][]> {
        let params: HttpParams = new HttpParams();
        params = params.set("text", text);

        return this.http.get<[string,string][]>(backendUrl + "/queryBills", { params: params });
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

        if (page.sortKey!= null) {
            params = params.set("sortKey", page.sortKey);
        }

        return firstValueFrom(this.http.get<Legislator[]>(backendUrl + "/getLegislators", { params: params }));
    }

    getLegislatorPageData(state: string | null = null): Promise<LegislatorPageData> {
        let params: HttpParams = new HttpParams();

        if (state != null) {
            params = params.set("state", state);
        }

        return firstValueFrom(this.http.get<LegislatorPageData>(backendUrl + "/getLegislatorPageData", { params: params }));
    }

    getBill(billId: string) {
        let params: HttpParams = new HttpParams();
        params = params.set("id", billId);

        return firstValueFrom(this.http.get<Bill>(backendUrl + "/getBill", { params: params }));
    }

}
