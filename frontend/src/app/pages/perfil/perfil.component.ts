import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, HostListener, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { API_BASE_URL } from '../../api.config';

interface PerfilUsuario {
  id: number;
  nombre: string;
  correo: string;
  telefono: string | null;
  username: string;
  activo: boolean;
  fechaCreacion: string;
  rol: string;
  fotoPerfil: string | null;
}

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './perfil.component.html',
  styleUrl: './perfil.component.css'
})
export class PerfilComponent implements OnInit {
  profile: PerfilUsuario | null = null;
  profileDraft = { nombre: '', correo: '', telefono: '', username: '' };
  activeTab: 'overview' | 'edit' = 'overview';
  loading = true;
  busy = false;
  errorMessage = '';
  photoEditorSource = '';
  expandedProfileImage = '';
  photoZoom = 1;
  photoOffsetX = 0;
  photoOffsetY = 0;
  private cropImage: HTMLImageElement | null = null;
  private cropPointer: { x: number; y: number; offsetX: number; offsetY: number } | null = null;
  private readonly cropFrameSize = 240;

  constructor(private http: HttpClient) {}

  ngOnInit(): void { this.loadProfile(); }

  openProfileImage(): void {
    this.expandedProfileImage = this.profile?.fotoPerfil || '';
  }

  closeProfileImage(): void {
    this.expandedProfileImage = '';
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void { this.closeProfileImage(); }

  selectTab(tab: 'overview' | 'edit'): void {
    this.errorMessage = '';
    this.activeTab = tab;
    if (tab === 'edit' && this.profile) {
      this.profileDraft = {
        nombre: this.profile.nombre,
        correo: this.profile.correo,
        telefono: this.profile.telefono || '',
        username: this.profile.username
      };
    }
  }

  saveProfile(): void {
    if (!this.profileDraft.nombre.trim() || !this.profileDraft.correo.trim() || !this.profileDraft.username.trim()) {
      this.errorMessage = 'Completa nombre, correo y nombre de usuario.';
      return;
    }
    this.busy = true;
    this.errorMessage = '';
    this.http.put<PerfilUsuario>(`${API_BASE_URL}/auth/me/profile`, {
      nombre: this.profileDraft.nombre.trim(),
      correo: this.profileDraft.correo.trim(),
      telefono: this.profileDraft.telefono.trim(),
      username: this.profileDraft.username.trim()
    }).subscribe({
      next: profile => {
        this.profile = profile;
        localStorage.setItem('username', profile.username);
        this.busy = false;
        this.activeTab = 'overview';
      },
      error: error => {
        this.busy = false;
        this.errorMessage = error?.error?.message || 'No se pudieron guardar los cambios. Revisa los datos ingresados.';
      }
    });
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    this.errorMessage = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.errorMessage = 'Selecciona un archivo de imagen.';
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.errorMessage = 'La imagen debe pesar menos de 10 MB.';
      return;
    }
    const reader = new FileReader();
    reader.onerror = () => this.errorMessage = 'No se pudo leer la imagen. Intenta con otra.';
    reader.onload = () => {
      const image = new Image();
      image.onerror = () => this.errorMessage = 'No se pudo abrir la imagen. Intenta con otra.';
      image.onload = () => {
        this.cropImage = image;
        this.photoEditorSource = String(reader.result);
        this.photoZoom = 1;
        this.photoOffsetX = 0;
        this.photoOffsetY = 0;
      };
      image.src = String(reader.result);
    };
    reader.readAsDataURL(file);
  }

  setPhotoZoom(event: Event): void {
    this.photoZoom = Number((event.target as HTMLInputElement).value);
    this.clampPhotoOffset();
  }

  photoCropTransform(): string {
    return `translate(calc(-50% + ${this.photoOffsetX}px), calc(-50% + ${this.photoOffsetY}px)) scale(${this.photoZoom})`;
  }

  startPhotoCrop(event: PointerEvent): void {
    event.preventDefault();
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.cropPointer = { x: event.clientX, y: event.clientY, offsetX: this.photoOffsetX, offsetY: this.photoOffsetY };
  }

  movePhotoCrop(event: PointerEvent): void {
    if (!this.cropPointer) return;
    this.photoOffsetX = this.cropPointer.offsetX + event.clientX - this.cropPointer.x;
    this.photoOffsetY = this.cropPointer.offsetY + event.clientY - this.cropPointer.y;
    this.clampPhotoOffset();
  }

  stopPhotoCrop(): void { this.cropPointer = null; }

  cancelPhotoCrop(): void {
    this.photoEditorSource = '';
    this.cropImage = null;
    this.cropPointer = null;
  }

  applyPhotoCrop(): void {
    if (!this.cropImage) return;
    const size = 512;
    const scale = Math.max(size / this.cropImage.naturalWidth, size / this.cropImage.naturalHeight) * this.photoZoom;
    const width = this.cropImage.naturalWidth * scale;
    const height = this.cropImage.naturalHeight * scale;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const context = canvas.getContext('2d');
    if (!context) {
      this.errorMessage = 'No se pudo preparar la imagen en este navegador.';
      return;
    }
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, size, size);
    const offsetScale = size / this.cropFrameSize;
    context.drawImage(this.cropImage, (size - width) / 2 + this.photoOffsetX * offsetScale,
      (size - height) / 2 + this.photoOffsetY * offsetScale, width, height);
    const dataUrl = canvas.toDataURL('image/jpeg', 0.84);
    this.cancelPhotoCrop();
    this.savePhoto(dataUrl);
  }

  createdDate(): string {
    if (!this.profile?.fechaCreacion) return '—';
    const date = new Date(this.profile.fechaCreacion);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString('es-SV', { year: 'numeric', month: 'long', day: 'numeric' });
  }

  private loadProfile(): void {
    this.http.get<PerfilUsuario>(`${API_BASE_URL}/auth/me/profile`).subscribe({
      next: profile => { this.profile = profile; this.loading = false; },
      error: () => { this.errorMessage = 'No se pudo cargar el perfil. Comprueba que Spring esté ejecutándose.'; this.loading = false; }
    });
  }

  private savePhoto(fotoPerfil: string): void {
    this.busy = true;
    this.http.put<PerfilUsuario>(`${API_BASE_URL}/auth/me/foto`, { fotoPerfil }).subscribe({
      next: profile => { this.profile = profile; this.busy = false; this.errorMessage = ''; },
      error: error => {
        this.busy = false;
        this.errorMessage = error?.status === 413
          ? 'La imagen es demasiado grande. Prueba con otra.'
          : 'No se pudo guardar la foto. Inténtalo nuevamente.';
      }
    });
  }

  private clampPhotoOffset(): void {
    const limit = Math.max(0, (this.photoZoom - 1) * this.cropFrameSize / 2);
    this.photoOffsetX = Math.max(-limit, Math.min(limit, this.photoOffsetX));
    this.photoOffsetY = Math.max(-limit, Math.min(limit, this.photoOffsetY));
  }
}
