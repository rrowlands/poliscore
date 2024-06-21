import { animate, animateChild, group, query, style, transition, trigger } from "@angular/animations";

export const slideInAnimation =
  trigger('routeAnimations', [
    transition('poliscorePage => legislatorPage', [
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
    transition('legislatorPage => poliscorePage', [
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
      ])
  ]);