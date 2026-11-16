import { Component, Input, ChangeDetectionStrategy } from '@angular/core';


@Component({
  selector: 'app-kpi-card',
  standalone: true,
  imports: [],
  templateUrl: './kpi-card.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./kpi-card.component.css']
})
export class KpiCardComponent {
  @Input() title: string = '';
    @Input() value: string | number | null = null;
  @Input() icon: string = '';
    @Input() delta: string | number | null = null;
}
