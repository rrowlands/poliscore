import { animate, animateChild, group, query, style, transition, trigger } from "@angular/animations";

export const slideInAnimation =
  trigger('routeAnimations', [
    transition('poliscorePage => legislatorsPage', [
      style({ position: 'relative' }),
      query(':enter, :leave', [
        style({
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%'
        })
      ]),
      query(':enter', [
        style({ left: '100%' })
      ], { optional: true }),
      query(':leave', animateChild(), { optional: true }),
      group([
        query(':leave', [
          animate('500ms ease-out', style({ left: '-100%' }))
        ], { optional: true }),
        query(':enter', [
          animate('500ms ease-out', style({ left: '0%' }))
        ], { optional: true }),
      ]),
    ]),
    transition('legislatorsPage => poliscorePage', [
      style({ position: 'relative' }),
      query(':enter, :leave', [
        style({
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%'
        })
      ]),
      query(':enter', [
        style({ left: '-100%' })
      ], { optional: true }),
      query(':leave', animateChild(), { optional: true }),
      group([
        query(':leave', [
          animate('500ms ease-out', style({ left: '100%' }))
        ], { optional: true }),
        query(':enter', [
          animate('500ms ease-out', style({ left: '0%' }))
        ], { optional: true }),
      ])
    ]),
    transition('legislatorsPage => legislatorPage', [
      style({ position: 'relative' }),
      query(':enter, :leave', [
        style({
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%'
        })
      ]),
      query(':enter', [
        style({ left: '100%' })
      ], { optional: true }),
      query(':leave', animateChild(), { optional: true }),
      group([
        query(':leave', [
          animate('500ms ease-out', style({ transform: 'scale(2.2, 3)' }))
        ], { optional: true }),
        query(':enter', [
          animate('500ms ease-out', style({ transform: 'scale(0.5)' }))
        ], { optional: true }),
      ]),
    ]),
    transition('legislatorPage => legislatorsPage', [
      style({ position: 'relative' }),
      query(':enter .tran-div, :leave .tran-div', [
        style({
          position: 'absolute',
        })
      ]),
      query(':enter .tran-div', [
        style({ visibility: 'hidden' })
      ], { optional: true }),
      query(':leave .tran-div', animateChild(), { optional: true }),
      group([
        query(':leave .tran-div', [
          animate('500ms ease-out', style({ transform: 'scale(0.4, 0.3)' }))
        ], { optional: true }),
        query(':enter > div', [
          animate('500ms ease-out', style({ visibility: 'visible' }))
        ], { optional: true }),
      ]),
    ])
  ]);