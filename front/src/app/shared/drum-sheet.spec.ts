import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { DrumPattern, DrumVoice } from '../models';
import { DrumSheetComponent } from './drum-sheet';

const GROOVE: DrumPattern = {
  version: 1,
  timeSignature: [4, 4],
  stepsPerBeat: 4,
  tuplet: false,
  bars: 1,
  voices: ['hihat', 'snare', 'kick'],
  hits: {
    hihat: [0, 2, 4, 6, 8, 10, 12, 14],
    snare: [4, 12],
    kick: [0, 8],
  },
};

describe('DrumSheetComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [DrumSheetComponent] });
  });

  it('renderiza um SVG com pauta, cabecas e playhead sem erro', () => {
    const fixture = TestBed.createComponent(DrumSheetComponent);
    fixture.componentRef.setInput('pattern', GROOVE);
    fixture.detectChanges();

    const svg: SVGSVGElement | null = fixture.nativeElement.querySelector('svg');
    expect(svg).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.stafflines').length).toBe(5);
    expect(fixture.nativeElement.querySelectorAll('.nh, .nh-x').length).toBeGreaterThan(0);
    expect(fixture.nativeElement.querySelectorAll('.ph').length).toBe(1);
  });

  it('playhead acompanha playheadPos (0..1) sobre a largura do loop', () => {
    const fixture = TestBed.createComponent(DrumSheetComponent);
    fixture.componentRef.setInput('pattern', GROOVE);
    fixture.componentRef.setInput('playheadPos', 0);
    fixture.detectChanges();
    const ph = fixture.nativeElement.querySelector('.ph') as SVGLineElement;
    const x0 = Number(ph.getAttribute('x1'));

    fixture.componentRef.setInput('playheadPos', 0.5);
    fixture.detectChanges();
    const xMid = Number(ph.getAttribute('x1'));

    expect(x0).toBe(50); // padX
    expect(xMid).toBeGreaterThan(x0);
  });

  it('nao emite nada ao clicar num dot quando editable=false', () => {
    const fixture = TestBed.createComponent(DrumSheetComponent);
    fixture.componentRef.setInput('pattern', GROOVE);
    fixture.detectChanges();

    let emitted = false;
    fixture.componentInstance.stepToggle.subscribe(() => (emitted = true));
    (fixture.nativeElement.querySelector('.dot') as SVGElement).dispatchEvent(new Event('click'));
    expect(emitted).toBe(false);
  });

  it('emite stepToggle e patternChange ao clicar num dot quando editable=true', () => {
    const fixture = TestBed.createComponent(DrumSheetComponent);
    fixture.componentRef.setInput('pattern', GROOVE);
    fixture.componentRef.setInput('editable', true);
    fixture.detectChanges();

    const toggles: Array<{ voice: DrumVoice; step: number }> = [];
    const patterns: DrumPattern[] = [];
    fixture.componentInstance.stepToggle.subscribe((e) => toggles.push(e));
    fixture.componentInstance.patternChange.subscribe((p) => patterns.push(p));

    const dot = fixture.nativeElement.querySelector('circle.dot') as SVGElement;
    const voice = dot.getAttribute('data-voice') as DrumVoice;
    const step = Number(dot.getAttribute('data-step'));
    dot.dispatchEvent(new Event('click'));

    expect(toggles).toEqual([{ voice, step }]);
    expect(patterns).toHaveLength(1);
    // toggle imutavel: o pattern emitido difere do original
    expect(patterns[0]).not.toBe(GROOVE);
  });
});
