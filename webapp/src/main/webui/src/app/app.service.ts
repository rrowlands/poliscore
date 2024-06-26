
import { Injectable } from '@angular/core';
import { HttpHeaders, HttpClient, HttpParams } from '@angular/common/http';
import { backendUrl } from './app.config';
import { Legislator } from './model';
import { firstValueFrom } from 'rxjs/internal/firstValueFrom';

@Injectable()
export class AppService {

    constructor(private http: HttpClient) { }

    getLegislator(id: string): Promise<Legislator | undefined> {
        let params: HttpParams = new HttpParams();
        params = params.set("id", id);

        return firstValueFrom(this.http.get<Legislator>(backendUrl + "/getLegislator", { params: params }));
    }

    getLegislators(): Promise<Legislator[]> {
        let params: HttpParams = new HttpParams();

        return firstValueFrom(this.http.get<Legislator[]>(backendUrl + "/getLegislators", { params: params }));
    }

}
