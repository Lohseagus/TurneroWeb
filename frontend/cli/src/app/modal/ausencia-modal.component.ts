import { Component, Input, ChangeDetectionStrategy } from '@angular/core';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'app-ausencia-modal',
    standalone: true,
    imports: [],
    templateUrl: './ausencia-modal.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './ausencia-modal.component.css'
})
export class AusenciaModalComponent {
    @Input() nombrePaciente: string = 'Paciente';
    @Input() fecha: string = '';
    @Input() horaInicio: string = '';
    @Input() horaFin: string = '';

    constructor(public activeModal: NgbActiveModal) { }

    /**
     * Confirma la ausencia del paciente
     */
    confirmarAusencia() {
        this.activeModal.close(true); // true = confirma la ausencia
    }

    /**
     * Cancela la operación
     */
    cancelar() {
        this.activeModal.dismiss(false); // false = cancela
    }
}
