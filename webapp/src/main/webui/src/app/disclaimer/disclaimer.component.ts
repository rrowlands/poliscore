import { Component, Inject, Input } from '@angular/core';
import { MatDialog, MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { BillMetadata } from '../model';

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

  @Input() public positionBelow: boolean = false;

  @Input() public metadata!: BillMetadata;

  public tooltipVisible = true;

  constructor(public dialog: MatDialog) {}

  openDialog(): void {
    this.tooltipVisible = false;
    this.dialog.open(DisclaimerDialogComponent, {
      data: { large: this.large, metadata: this.metadata, disclaimerComponent: this }
    });
  }
}

@Component({
  selector: 'disclaimer-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  template: `
    <div mat-dialog-content>
        <span *ngIf="data.large == ''">
          <p>The data on this page was generated using OpenAI's {{data.metadata.model.replace("gpt","GPT")}} on {{data.metadata.date}}. Studies have shown GPT-4 to have a left-wing political bias. Additionally, LLMs work by parroting back training data and do not necessarily root all opinions in concrete morals or logically consistent world views. Please consider this information as part of an experimental AI analysis rather than definitive or authoritative guidance.
          <br/><br/>
          For verification, you can cross-reference details with the official Congress website by clicking on the bill title. You can also explore aggregated data by clicking on the bill sponsor or <a href="/">viewing all legislators</a>. For more information about our methods, including the prompts used and the goals of this project, <a href="/about">visit the About page.</a>
          </p>
        </span>
        <span *ngIf="data.large != ''" style="white-space: pre-line">
          <p [innerHtml]="data.large"></p>
        </span>
    </div>
    <div mat-dialog-actions align="center">
      <button mat-button (click)="onClose()">Close</button>
    </div>
  `,
})
export class DisclaimerDialogComponent {
  constructor(
    @Inject(MAT_DIALOG_DATA) public data: { large: string, metadata: BillMetadata, disclaimerComponent: any },
    public dialogRef: MatDialogRef<DisclaimerDialogComponent>
  ) {}

  onClose(): void {
    this.data.disclaimerComponent.tooltipVisible = true;
    this.dialogRef.close();
  }
}
