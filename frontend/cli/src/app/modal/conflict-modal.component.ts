
import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-conflict-modal',
  standalone: true,
  imports: [],
  templateUrl: './conflict-modal.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './conflict-modal.component.css'
})
export class ConflictModalComponent {
  constructor(public modal: NgbActiveModal) {}

  @Input() title: string = 'Conflicto de Solapamiento';
  @Input() headerMessage: string = '';
  @Input() conflicts: Array<any> = [];
  @Input() confirmLabel: string = 'Crear sobreturno';

  close(): void {
    this.modal.close(true);
  }

  dismiss(): void {
    this.modal.dismiss();
  }
}
