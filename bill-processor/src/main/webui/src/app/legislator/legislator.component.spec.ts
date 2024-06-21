import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LegislatorComponent } from './legislator.component';

describe('LegislatorComponent', () => {
  let component: LegislatorComponent;
  let fixture: ComponentFixture<LegislatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LegislatorComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(LegislatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
