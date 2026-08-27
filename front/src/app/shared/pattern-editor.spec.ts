import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { DrumPattern } from '../models';
import { PatternEditorComponent } from './pattern-editor';
import { PATTERN_PRESETS } from './pattern-presets';

function baseline(over: Partial<DrumPattern> = {}): DrumPattern {
  return {
    version: 1,
    timeSignature: [4, 4],
    stepsPerBeat: 4,
    tuplet: false,
    bars: 1,
    voices: ['hihat', 'snare', 'kick'],
    hits: {},
    ...over,
  };
}

describe('PatternEditorComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PatternEditorComponent] });
  });

  function mount(pattern: DrumPattern) {
    const fixture = TestBed.createComponent(PatternEditorComponent);
    fixture.componentRef.setInput('pattern', pattern);
    const emitted: DrumPattern[] = [];
    fixture.componentInstance.patternChange.subscribe((p) => emitted.push(p));
    fixture.detectChanges();
    return { fixture, emitted };
  }

  it('clicar num botao de preset emite o DrumPattern daquele preset', () => {
    const { fixture, emitted } = mount(baseline());

    const presetGroup = fixture.nativeElement.querySelectorAll('.pe-group')[0] as HTMLElement;
    const buttons = presetGroup.querySelectorAll('.pe-chip');
    // Ordem dos botoes == ordem de PATTERN_PRESETS.
    (buttons[1] as HTMLButtonElement).click();

    expect(emitted).toHaveLength(1);
    expect(emitted[0]).toEqual(PATTERN_PRESETS[1].pattern);
    // Copia profunda - nunca o mesmo objeto do array compartilhado.
    expect(emitted[0]).not.toBe(PATTERN_PRESETS[1].pattern);
    expect(emitted[0].hits).not.toBe(PATTERN_PRESETS[1].pattern.hits);
  });

  it('mudar bars descarta hits fora do novo total', () => {
    // bars=2 -> total = 2*4*4 = 32; hits em 20 e 31 estao no 2o compasso.
    const { fixture, emitted } = mount(
      baseline({ bars: 2, voices: ['snare'], hits: { snare: [0, 4, 20, 31] } }),
    );

    const numberInputs = fixture.nativeElement.querySelectorAll(
      'input[type="number"]',
    ) as NodeListOf<HTMLInputElement>;
    // [numerador, stepsPerBeat, compassos] - compassos e o ultimo.
    const barsInput = numberInputs[numberInputs.length - 1];
    barsInput.value = '1';
    barsInput.dispatchEvent(new Event('change'));

    expect(emitted).toHaveLength(1);
    // total agora 16 - 20 e 31 caem fora.
    expect(emitted[0].bars).toBe(1);
    expect(emitted[0].hits['snare']).toEqual([0, 4]);
  });

  it('stepToggle do <app-drum-sheet> propaga um patternChange', () => {
    const { fixture, emitted } = mount(baseline({ voices: ['snare'], hits: {} }));

    const dot = fixture.nativeElement.querySelector('circle.dot') as SVGElement;
    dot.dispatchEvent(new Event('click'));

    expect(emitted).toHaveLength(1);
    const voice = dot.getAttribute('data-voice') as 'snare';
    const step = Number(dot.getAttribute('data-step'));
    expect(emitted[0].hits[voice]).toContain(step);
  });
});
