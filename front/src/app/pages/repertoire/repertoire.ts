import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../../api';
import {
  CreateRepertoireItemRequest,
  RepertoireItem,
  RepertoireItemStatus,
  RepertoireLinkRequest,
  UpdateRepertoireItemRequest,
} from '../../models';
import { Badge } from '../../shared/badge';
import { repertoireStatusLabel } from '../../shared/labels';

interface LinkDraft {
  url: string;
  label: string;
}

@Component({
  selector: 'app-repertoire',
  imports: [FormsModule, Badge],
  templateUrl: './repertoire.html',
})
export class Repertoire {
  private readonly api = inject(ApiService);

  protected readonly repertoireStatusLabel = repertoireStatusLabel;

  protected readonly items = signal<RepertoireItem[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  // Novo item
  protected newSongTitle = '';
  protected newArtist = '';
  protected newTargetBpm: number | null = null;
  protected newCurrentBpm: number | null = null;
  protected newNotes = '';
  protected newLinks: LinkDraft[] = [{ url: '', label: '' }];

  // Edição inline (status/BPM atual/notas/novo link)
  protected readonly editingId = signal<number | null>(null);
  protected editStatus: RepertoireItemStatus = 'NOT_STARTED';
  protected editCurrentBpm: number | null = null;
  protected editNotes = '';
  protected editNewLinkUrl = '';
  protected editNewLinkLabel = '';

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listRepertoireItems().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Falha ao carregar repertório: ' + (err?.message ?? err));
        this.loading.set(false);
      },
    });
  }

  protected addLinkRow(): void {
    this.newLinks = [...this.newLinks, { url: '', label: '' }];
  }

  protected removeLinkRow(index: number): void {
    this.newLinks = this.newLinks.filter((_, i) => i !== index);
  }

  protected createItem(): void {
    const songTitle = this.newSongTitle.trim();
    if (!songTitle) {
      return;
    }
    const links: RepertoireLinkRequest[] = this.newLinks
      .filter((l) => l.url.trim())
      .map((l) => ({ url: l.url.trim(), label: l.label.trim() || null }));

    const request: CreateRepertoireItemRequest = {
      songTitle,
      artist: this.newArtist.trim() || null,
      targetBpm: this.newTargetBpm,
      currentBpm: this.newCurrentBpm,
      notes: this.newNotes.trim() || null,
      links,
    };
    this.api.createRepertoireItem(request).subscribe({
      next: () => {
        this.newSongTitle = '';
        this.newArtist = '';
        this.newTargetBpm = null;
        this.newCurrentBpm = null;
        this.newNotes = '';
        this.newLinks = [{ url: '', label: '' }];
        this.reload();
      },
      error: (err) => {
        this.error.set('Falha ao criar item de repertório: ' + (err?.message ?? err));
      },
    });
  }

  protected startEdit(item: RepertoireItem): void {
    this.editingId.set(item.id);
    this.editStatus = item.status;
    this.editCurrentBpm = item.currentBpm;
    this.editNotes = item.notes ?? '';
    this.editNewLinkUrl = '';
    this.editNewLinkLabel = '';
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
  }

  protected saveEdit(item: RepertoireItem): void {
    const newLinks: RepertoireLinkRequest[] = this.editNewLinkUrl.trim()
      ? [{ url: this.editNewLinkUrl.trim(), label: this.editNewLinkLabel.trim() || null }]
      : [];
    const request: UpdateRepertoireItemRequest = {
      status: this.editStatus,
      currentBpm: this.editCurrentBpm,
      notes: this.editNotes.trim() || null,
      newLinks,
    };
    this.api.updateRepertoireItem(item.id, request).subscribe({
      next: () => {
        this.editingId.set(null);
        this.reload();
      },
      error: (err) => {
        this.error.set('Falha ao atualizar item de repertório: ' + (err?.message ?? err));
      },
    });
  }
}
