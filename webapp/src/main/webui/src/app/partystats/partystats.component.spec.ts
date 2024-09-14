import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PartystatsComponent } from './partystats.component';

describe('PartystatsComponent', () => {
  let component: PartystatsComponent;
  let fixture: ComponentFixture<PartystatsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PartystatsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PartystatsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
