import { Component, HostListener, Inject, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatDialog, MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'about',
  standalone: true,
  imports: [RouterModule, CommonModule, MatDialogModule],
  templateUrl: './about.component.html',
  styleUrl: './about.component.scss'
})
export class AboutComponent implements OnInit {

  public isPreload = true;

  public donateBarHidden = true;

  constructor(public dialog: MatDialog) { }

  ngOnInit(): void {
    setTimeout(() => {
      this.isPreload = false;
    }, 100);
  }

  onScroll(e: any) {
    const el = e.target;

    let scrollAmt = el.offsetHeight + el.scrollTop;

    if (scrollAmt >= (el.scrollHeight * 0.30) && scrollAmt <= (el.scrollHeight - 1000)) {
      this.donateBarHidden = false;
    } else {
      this.donateBarHidden = true;
    }
  }

  public captureEmailForm(): void {
    window.location.href = "https://2d35a37e.sibforms.com/serve/MUIFABzv_pK1_YgaT0O9h369Fe89iBz1lmE63oAo2cuHjvcQmATp3Juz4BudHm6zdwwIAraE4YGla-0G121m2DEC-RQP_YUO98T5a5ciR33HDYJnFAyYATNoiO6H5PQWTPfkfYJOae2Rx_J52Ag3H4B8I--ljBdvugyb0oQdfxaOFEamGNOGHPfBEaEA-yFacsvAN7oZRyaOXKcB";
  }

  clickPrivacyPolicy() {
    this.dialog.open(DisclaimerDialogComponent);
  }
}

@Component({
  selector: 'disclaimer-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  template: `
    <div mat-dialog-content>
        <p>PoliScore is committed to protecting your privacy. We use Google Analytics with default settings to collect basic, non-personal data such as the pages you visit and the links you click. This data helps us understand how users interact with our website and improve its functionality. We do not collect or store personally identifiable information, nor do we sell or share any data with third parties.</p>

        <br/>
        <p>Google Analytics processes this information in accordance with <a href="https://policies.google.com/privacy">their Privacy Policy</a>. If you prefer not to be tracked, you can opt out using the <a href="https://tools.google.com/dlpage/gaoptout/">Google Analytics Opt-Out Browser Add-on</a> or by adjusting your browser settings to block tracking scripts. By using our website, you agree to the terms of this policy.</p>
    </div>
    <div mat-dialog-actions align="center">
      <button mat-button (click)="onClose()">Close</button>
    </div>
  `,
})
export class DisclaimerDialogComponent {
  constructor(
    @Inject(MAT_DIALOG_DATA) public data: { large: string, disclaimerComponent: any },
    public dialogRef: MatDialogRef<DisclaimerDialogComponent>
  ) {}

  onClose(): void {
    this.dialogRef.close();
  }
}
