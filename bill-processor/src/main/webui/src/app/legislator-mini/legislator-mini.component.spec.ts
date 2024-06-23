import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LegislatorMiniComponent } from './legislator-mini.component';

describe('LegislatorMiniComponent', () => {
  let component: LegislatorMiniComponent;
  let fixture: ComponentFixture<LegislatorMiniComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LegislatorMiniComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(LegislatorMiniComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
