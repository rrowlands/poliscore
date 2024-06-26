import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PoliscoreComponent } from './poliscore.component';

describe('PoliscoreComponent', () => {
  let component: PoliscoreComponent;
  let fixture: ComponentFixture<PoliscoreComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PoliscoreComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(PoliscoreComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
