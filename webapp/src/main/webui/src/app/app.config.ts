import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { routes } from './app.routes';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

export const appConfig: ApplicationConfig = {
  providers: [provideRouter(routes), provideAnimations(), provideAnimationsAsync()]
};

export const backendUrl: string = "https://2e5a2qaqb7sroosbbgexgxzg2i0sdhmq.lambda-url.us-east-1.on.aws";
