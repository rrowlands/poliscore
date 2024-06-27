import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { routes } from './app.routes';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

export const appConfig: ApplicationConfig = {
  providers: [provideRouter(routes), provideAnimations(), provideAnimationsAsync()]
};

export const backendUrl: string = "https://h66r5lk6xcbyhbqemd53jl5m2e0uorod.lambda-url.us-east-1.on.aws";
