import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../../api.config';

type RolUsuario = 'Administrador' | 'Usuario';
type EstadoUsuario = 'Activo' | 'Inactivo';

interface UsuarioVista {
  id: number;
  nombre: string;
  correo: string;
  username: string;
  rol: RolUsuario;
  estado: EstadoUsuario;
  fechaAlta: string;
}

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './usuarios.component.html',
  styleUrl: './usuarios.component.css'
})
export class UsuariosComponent implements OnInit, OnDestroy {
  usuarios: UsuarioVista[] = [];
  cargando = true;
  errorCarga = '';
  guardando = false;
  eliminando = false;
  notificacion: { tipo: 'exito' | 'error'; mensaje: string } | null = null;
  errorOperacion = '';
  private temporizadorNotificacion?: number;

  constructor(private readonly http: HttpClient) {}

  ngOnInit(): void { this.cargarUsuarios(); }

  ngOnDestroy(): void {
    if (this.temporizadorNotificacion !== undefined) window.clearTimeout(this.temporizadorNotificacion);
  }

  private mostrarNotificacion(tipo: 'exito' | 'error', mensaje: string): void {
    this.notificacion = { tipo, mensaje };
    if (this.temporizadorNotificacion !== undefined) window.clearTimeout(this.temporizadorNotificacion);
    this.temporizadorNotificacion = window.setTimeout(() => this.notificacion = null, 5000);
  }

  private mensajeErrorApi(error: any, alternativa: string): string {
    const mensaje = error?.error?.message;
    if (typeof mensaje === 'string' && mensaje.trim() && !/(could not execute|constraint|foreign key|\bSQL\b)/i.test(mensaje)) {
      return mensaje;
    }
    return alternativa;
  }

  cargarUsuarios(): void {
    this.cargando = true;
    this.errorCarga = '';
    this.http.get<Array<{id:number;nombre:string;correo:string;username:string;activo:boolean;fechaCreacion:string;rol:string}>>(`${API_BASE_URL}/usuarios`)
      .subscribe({
        next: (registros) => {
          this.usuarios = registros.map((u) => ({
            id: u.id, nombre: u.nombre, correo: u.correo, username: u.username,
            rol: u.rol === 'Administrador' ? 'Administrador' : 'Usuario',
            estado: u.activo ? 'Activo' : 'Inactivo',
            fechaAlta: u.fechaCreacion ? new Date(u.fechaCreacion).toLocaleDateString('es-SV') : '—'
          }));
          this.cargando = false;
        },
        error: () => {
          this.errorCarga = 'No se pudieron cargar los usuarios desde el servidor.';
          this.cargando = false;
          this.mostrarNotificacion('error', this.errorCarga);
        }
      });
  }

  busqueda = '';
  filtroRol = '';
  filtroEstado = 'Activo';
  modalAbierto = false;
  modalEliminarAbierto = false;
  editandoId: number | null = null;
  eliminandoId: number | null = null;
  errorFormulario = '';
  formulario = this.formularioVacio();

  get usuariosFiltrados(): UsuarioVista[] {
    const termino = this.busqueda.trim().toLocaleLowerCase();
    return this.usuarios.filter((usuario) => {
      const coincideBusqueda = !termino || `${usuario.nombre} ${usuario.correo}`.toLocaleLowerCase().includes(termino);
      const coincideRol = !this.filtroRol || usuario.rol === this.filtroRol;
      const coincideEstado = !this.filtroEstado || usuario.estado === this.filtroEstado;
      return coincideBusqueda && coincideRol && coincideEstado;
    });
  }

  get activos(): number { return this.usuarios.filter((usuario) => usuario.estado === 'Activo').length; }
  get administradores(): number { return this.usuarios.filter((usuario) => usuario.rol === 'Administrador').length; }

  abrirCrear(): void {
    this.editandoId = null;
    this.formulario = this.formularioVacio();
    this.errorFormulario = '';
    this.modalAbierto = true;
  }

  abrirEditar(usuario: UsuarioVista): void {
    this.editandoId = usuario.id;
    this.formulario = { nombre: usuario.nombre, correo: usuario.correo, username: usuario.username, password: '', rol: usuario.rol, estado: usuario.estado };
    this.errorFormulario = '';
    this.modalAbierto = true;
  }

  guardar(): void {
    const nombre = this.formulario.nombre.trim();
    const correo = this.formulario.correo.trim().toLocaleLowerCase();
    const username = this.formulario.username.trim();
    if (!nombre || !correo || !username || (this.editandoId === null && this.formulario.password.length < 8)) {
      this.errorFormulario = 'Completa todos los datos requeridos. La contraseña debe tener al menos 8 caracteres.';
      return;
    }
    const correoEnUso = this.usuarios.some((usuario) => usuario.correo.toLocaleLowerCase() === correo && usuario.id !== this.editandoId);
    if (correoEnUso) {
      this.errorFormulario = 'Ya existe un usuario con ese correo.';
      return;
    }

    const payload: Record<string, unknown> = { nombre, correo, username, rol: this.formulario.rol, activo: this.formulario.estado === 'Activo' };
    if (this.formulario.password) payload['password'] = this.formulario.password;
    this.guardando = true;
    this.errorFormulario = '';
    const peticion = this.editandoId === null
      ? this.http.post(`${API_BASE_URL}/usuarios`, payload)
      : this.http.put(`${API_BASE_URL}/usuarios/${this.editandoId}`, payload);
    peticion.subscribe({
      next: () => {
        this.guardando = false;
        this.modalAbierto = false;
        this.mostrarNotificacion('exito', this.editandoId === null ? 'Usuario creado y guardado en la base de datos.' : 'Cambios guardados en la base de datos.');
        this.cargarUsuarios();
      },
      error: (error) => {
        this.guardando = false;
        this.errorFormulario = this.mensajeErrorApi(error, 'No se pudo guardar el usuario. Revisa los datos e inténtalo de nuevo.');
        this.mostrarNotificacion('error', this.errorFormulario);
      }
    });
  }

  solicitarEliminacion(usuario: UsuarioVista): void {
    this.eliminandoId = usuario.id;
    this.errorOperacion = '';
    this.modalEliminarAbierto = true;
  }

  eliminar(): void {
    if (this.eliminandoId === null) return;
    const id = this.eliminandoId;
    this.eliminando = true;
    this.http.delete(`${API_BASE_URL}/usuarios/${id}`).subscribe({
      next: () => {
        this.eliminando = false;
        this.modalEliminarAbierto = false;
        this.eliminandoId = null;
        this.mostrarNotificacion('exito', 'Usuario desactivado; sus movimientos y cuentas se conservaron.');
        this.cargarUsuarios();
      },
      error: (error) => {
        this.eliminando = false;
        this.errorOperacion = this.mensajeErrorApi(error, 'No se pudo desactivar el usuario. Inténtalo de nuevo.');
        this.mostrarNotificacion('error', this.errorOperacion);
      }
    });
  }

  cancelarModal(): void { this.modalAbierto = false; }
  cancelarEliminacion(): void { this.modalEliminarAbierto = false; this.eliminandoId = null; }

  private formularioVacio(): { nombre: string; correo: string; username: string; password: string; rol: RolUsuario; estado: EstadoUsuario } {
    return { nombre: '', correo: '', username: '', password: '', rol: 'Usuario', estado: 'Activo' };
  }
}
