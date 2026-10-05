import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { API_BASE_URL } from '../../api.config';

interface OpcionMenu {
  id: number | null;
  etiqueta: string;
  ruta: string;
  icono: string;
  orden: number;
  activo: boolean;
  roles: string[];
}

interface SeccionMenu {
  id: number | null;
  titulo: string;
  orden: number;
  activo: boolean;
  opciones: OpcionMenu[];
}

interface ConfiguracionMenu {
  secciones: SeccionMenu[];
  roles: string[];
}

@Component({
  selector: 'app-administrar-menu',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './administrar-menu.component.html',
  styleUrl: './administrar-menu.component.css'
})
export class AdministrarMenuComponent implements OnInit {
  secciones: SeccionMenu[] = [];
  roles: string[] = [];
  iconos = ['dashboard', 'cuentas', 'movimientos', 'calendario', 'usuarios', 'configuracion', 'reporte'];
  cargando = true;
  guardando = false;
  error = '';
  mensaje = '';
  confirmacionVisible = false;

  constructor(private readonly http: HttpClient) {}

  ngOnInit(): void { this.cargar(); }

  cargar(): void {
    this.cargando = true;
    this.http.get<ConfiguracionMenu>(`${API_BASE_URL}/menu-admin`).subscribe({
      next: respuesta => {
        this.secciones = respuesta.secciones || [];
        this.roles = respuesta.roles || [];
        this.cargando = false;
      },
      error: () => {
        this.error = 'No se pudo cargar la configuración del menú. Comprueba que Spring esté actualizado y que tu cuenta sea administradora.';
        this.cargando = false;
      }
    });
  }

  agregarSeccion(): void {
    this.secciones.push({ id: null, titulo: '', orden: this.secciones.length + 1, activo: true, opciones: [] });
  }

  eliminarSeccion(indice: number): void { this.secciones.splice(indice, 1); }

  agregarOpcion(seccion: SeccionMenu): void {
    seccion.opciones.push({ id: null, etiqueta: '', ruta: '', icono: 'dashboard', orden: seccion.opciones.length + 1, activo: true, roles: [] });
  }

  eliminarOpcion(seccion: SeccionMenu, indice: number): void { seccion.opciones.splice(indice, 1); }

  tieneRol(opcion: OpcionMenu, rol: string): boolean { return opcion.roles.includes(rol); }

  cambiarRol(opcion: OpcionMenu, rol: string, event: Event): void {
    const marcado = (event.target as HTMLInputElement).checked;
    opcion.roles = marcado
      ? [...new Set([...opcion.roles, rol])]
      : opcion.roles.filter(actual => actual !== rol);
  }

  guardar(): void {
    this.error = '';
    this.mensaje = '';
    const rutas = this.secciones.flatMap(seccion => seccion.opciones.map(opcion => opcion.ruta.trim()));
    if (this.secciones.some(seccion => !seccion.titulo.trim())
        || this.secciones.some(seccion => seccion.opciones.some(opcion => !opcion.etiqueta.trim() || !opcion.ruta.trim()))) {
      this.error = 'Completa el título de cada sección y el nombre y ruta de cada opción.';
      return;
    }
    if (rutas.some(ruta => !ruta.startsWith('/')) || new Set(rutas).size !== rutas.length) {
      this.error = 'Cada ruta debe comenzar con / y no puede repetirse.';
      return;
    }

    this.guardando = true;
    this.http.put<ConfiguracionMenu>(`${API_BASE_URL}/menu-admin`, { secciones: this.secciones }).subscribe({
      next: respuesta => {
        this.secciones = respuesta.secciones || [];
        this.roles = respuesta.roles || this.roles;
        this.guardando = false;
        this.mensaje = 'La configuración del menú se guardó correctamente.';
        this.confirmacionVisible = true;
      },
      error: err => {
        this.guardando = false;
        this.error = err?.error?.message || 'No se pudo guardar el menú. Revisa que se mantenga activa la opción Administrar menú para Administrador.';
      }
    });
  }

  irAlDashboard(): void {
    this.confirmacionVisible = false;
    // Recarga la configuración desde la base de datos para reflejar de inmediato el menú guardado.
    window.location.assign('/dashboard');
  }
}
