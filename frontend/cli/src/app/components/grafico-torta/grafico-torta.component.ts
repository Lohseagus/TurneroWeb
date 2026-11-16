import { Component, Input, ChangeDetectionStrategy } from '@angular/core';

import { BaseChartDirective } from 'ng2-charts';

@Component({
  selector: 'app-grafico-torta',
  standalone: true,
  imports: [BaseChartDirective],
  templateUrl: './grafico-torta.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./grafico-torta.component.css']
})
export class GraficoTortaComponent {
  @Input() labels: string[] = [];
  @Input() data: number[] = [];
  @Input() title: string = '';
  @Input() icon: string = '';

  public get chartData() {
    return {
      labels: this.labels,
      datasets: [{ data: this.data }]
    };
  }

  public hasData(): boolean {
    return this.data && this.data.length > 0 && this.data.some(value => value > 0);
  }
}
