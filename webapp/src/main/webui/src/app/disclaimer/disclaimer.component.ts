import { Component, Inject, Input } from '@angular/core';
import { MatDialog, MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'disclaimer',
  standalone: true,
  imports: [CommonModule, MatDialogModule],
  templateUrl: './disclaimer.component.html',
  styleUrl: './disclaimer.component.scss'
})
export class DisclaimerComponent {
  @Input() public small: string = "";

  @Input() public large: string = "";

  public tooltipVisible = true;

  constructor(public dialog: MatDialog) {}

  openDialog(): void {
    this.tooltipVisible = false;
    this.dialog.open(DisclaimerDialogComponent, {
      data: { large: this.large, disclaimerComponent: this }
    });
  }
}

@Component({
  selector: 'disclaimer-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  template: `
    <div mat-dialog-content>
        <p>The data on this page was generated using OpenAI's GPT-4o. While we strive for accuracy, this project’s ambitious scope and the inherent limitations of AI mean that some errors are likely, particularly in predictions about societal impacts and policy implications. Please consider this information as part of an experimental AI analysis rather than definitive or authoritative guidance.
        <br/><br/>
        For verification, you can cross-reference details with the official Congress website by clicking on the bill title. You can also explore aggregated data by clicking on the bill sponsor or <a href="/">viewing all legislators</a>. For more information about our methods, including the prompts used and the goals of this project, <a href="/about">visit the About page.</a>
        </p>
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
    this.data.disclaimerComponent.tooltipVisible = true;
    this.dialogRef.close();
  }
}
