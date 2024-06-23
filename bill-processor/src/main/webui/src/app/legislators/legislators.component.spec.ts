import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LegislatorsComponent } from './legislators.component';

describe('LegislatorsComponent', () => {
  let component: LegislatorsComponent;
  let fixture: ComponentFixture<LegislatorsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LegislatorsComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(LegislatorsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
