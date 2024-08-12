import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'about',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './about.component.html',
  styleUrl: './about.component.scss'
})
export class AboutComponent {

  constructor() { }

  public captureEmailForm(): void {
    window.location.href = "https://2d35a37e.sibforms.com/serve/MUIFABzv_pK1_YgaT0O9h369Fe89iBz1lmE63oAo2cuHjvcQmATp3Juz4BudHm6zdwwIAraE4YGla-0G121m2DEC-RQP_YUO98T5a5ciR33HDYJnFAyYATNoiO6H5PQWTPfkfYJOae2Rx_J52Ag3H4B8I--ljBdvugyb0oQdfxaOFEamGNOGHPfBEaEA-yFacsvAN7oZRyaOXKcB";
  }
}
