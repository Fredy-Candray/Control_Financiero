import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, HostListener, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { API_BASE_URL } from '../../api.config';
import { Cuenta } from '../../models/cuenta';

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
  fotoPortada: string | null;
  fotoPortadaOriginal: string | null;
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
  activeAccounts: Cuenta[] = [];
  accountsLoading = true;
  accountsError = '';
  profileDraft = { nombre: '', correo: '', telefono: '', username: '' };
  activeTab: 'overview' | 'edit' = 'overview';
  loading = true;
  busy = false;
  errorMessage = '';
  photoEditorSource = '';
  photoTarget: 'profile' | 'cover' = 'profile';
  cropFrameWidth = 240;
  cropFrameHeight = 240;
  expandedProfileImage = '';
  expandedImageLabel = 'Foto de perfil ampliada';
  photoZoom = 1;
  photoOffsetX = 0;
  photoOffsetY = 0;
  private cropImage: HTMLImageElement | null = null;
  private cropPointer: { x: number; y: number; offsetX: number; offsetY: number } | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void { this.loadProfile(); }

  openProfileImage(target: 'profile' | 'cover' = 'profile'): void {
    this.expandedProfileImage = (target === 'cover' ? (this.profile?.fotoPortadaOriginal || this.profile?.fotoPortada) : this.profile?.fotoPerfil) || '';
    this.expandedImageLabel = target === 'cover' ? 'Imagen de portada ampliada' : 'Foto de perfil ampliada';
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

  onPhotoSelected(event: Event, target: 'profile' | 'cover' = 'profile'): void {
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
        this.photoTarget = target;
        this.cropFrameWidth = Math.min(target === 'cover' ? 300 : 240, Math.max(180, window.innerWidth - 70));
        this.cropFrameHeight = target === 'cover' ? this.cropFrameWidth / 3 : this.cropFrameWidth;
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

  photoCropWidth(): number {
    if (!this.cropImage) return 0;
    return this.cropImage.naturalWidth * Math.max(this.cropFrameWidth / this.cropImage.naturalWidth, this.cropFrameHeight / this.cropImage.naturalHeight);
  }

  photoCropHeight(): number {
    if (!this.cropImage) return 0;
    return this.cropImage.naturalHeight * Math.max(this.cropFrameWidth / this.cropImage.naturalWidth, this.cropFrameHeight / this.cropImage.naturalHeight);
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
    const size = this.photoTarget === 'cover' ? 1500 : 512;
    const outputHeight = this.photoTarget === 'cover' ? 500 : 512;
    const scale = Math.max(size / this.cropImage.naturalWidth, outputHeight / this.cropImage.naturalHeight) * this.photoZoom;
    const width = this.cropImage.naturalWidth * scale;
    const height = this.cropImage.naturalHeight * scale;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = outputHeight;
    const context = canvas.getContext('2d');
    if (!context) {
      this.errorMessage = 'No se pudo preparar la imagen en este navegador.';
      return;
    }
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, size, outputHeight);
    const offsetScale = size / this.cropFrameWidth;
    context.drawImage(this.cropImage, (size - width) / 2 + this.photoOffsetX * offsetScale,
      (outputHeight - height) / 2 + this.photoOffsetY * offsetScale, width, height);
    let quality = 0.84;
    let dataUrl = canvas.toDataURL('image/jpeg', quality);
    while (dataUrl.length > 1_000_000 && quality > 0.4) {
      quality -= 0.1;
      dataUrl = canvas.toDataURL('image/jpeg', quality);
    }
    if (dataUrl.length > 1_000_000) {
      this.errorMessage = 'La imagen es demasiado grande. Prueba con otra.';
      return;
    }
    let original: string | undefined;
    if (this.photoTarget === 'cover') {
      if (/^data:image\/(jpeg|png|webp);base64,/.test(this.photoEditorSource)) {
        original = this.photoEditorSource;
      } else {
        const originalCanvas = document.createElement('canvas');
        originalCanvas.width = this.cropImage.naturalWidth;
        originalCanvas.height = this.cropImage.naturalHeight;
        const originalContext = originalCanvas.getContext('2d');
        if (!originalContext) {
          this.errorMessage = 'No se pudo preparar la imagen original.';
          return;
        }
        originalContext.fillStyle = '#ffffff';
        originalContext.fillRect(0, 0, originalCanvas.width, originalCanvas.height);
        originalContext.drawImage(this.cropImage, 0, 0);
        original = originalCanvas.toDataURL('image/jpeg', 0.9);
      }
      if (original.length > 15_000_000) {
        this.errorMessage = 'La imagen original es demasiado grande. Prueba con otra.';
        return;
      }
    }
    this.cancelPhotoCrop();
    this.savePhoto(dataUrl, original);
  }

  createdDate(): string {
    if (!this.profile?.fechaCreacion) return '—';
    const date = new Date(this.profile.fechaCreacion);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString('es-SV', { year: 'numeric', month: 'long', day: 'numeric' });
  }

  private loadProfile(): void {
    this.http.get<PerfilUsuario>(`${API_BASE_URL}/auth/me/profile`).subscribe({
      next: profile => { this.profile = profile; this.loading = false; this.loadActiveAccounts(profile.id); },
      error: () => { this.errorMessage = 'No se pudo cargar el perfil. Comprueba que Spring esté ejecutándose.'; this.loading = false; }
    });
  }

  private loadActiveAccounts(userId: number): void {
    this.accountsLoading = true;
    this.accountsError = '';
    this.http.get<Cuenta[]>(`${API_BASE_URL}/cuentas/usuario/${userId}`).subscribe({
      next: accounts => {
        this.activeAccounts = accounts.filter(account => account.activa);
        this.accountsLoading = false;
      },
      error: () => {
        this.accountsLoading = false;
        this.accountsError = 'No se pudieron cargar las cuentas activas.';
      }
    });
  }

  private savePhoto(fotoPerfil: string, fotoPortadaOriginal?: string): void {
    this.busy = true;
    const endpoint = this.photoTarget === 'cover' ? 'portada' : 'foto';
    const payload = this.photoTarget === 'cover' ? { fotoPortada: fotoPerfil, fotoPortadaOriginal } : { fotoPerfil };
    this.http.put<PerfilUsuario>(`${API_BASE_URL}/auth/me/${endpoint}`, payload).subscribe({
      next: profile => { this.profile = profile; this.busy = false; this.errorMessage = ''; },
      error: error => {
        this.busy = false;
        this.errorMessage = error?.status === 413
          ? 'La imagen es demasiado grande. Prueba con otra.'
          : 'No se pudo guardar la imagen. Inténtalo nuevamente.';
      }
    });
  }

  private clampPhotoOffset(): void {
    const limitX = Math.max(0, (this.photoCropWidth() * this.photoZoom - this.cropFrameWidth) / 2);
    const limitY = Math.max(0, (this.photoCropHeight() * this.photoZoom - this.cropFrameHeight) / 2);
    this.photoOffsetX = Math.max(-limitX, Math.min(limitX, this.photoOffsetX));
    this.photoOffsetY = Math.max(-limitY, Math.min(limitY, this.photoOffsetY));
  }
}
