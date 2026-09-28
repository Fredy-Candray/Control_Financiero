import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

type RolUsuario = 'Administrador' | 'Usuario';
type EstadoUsuario = 'Activo' | 'Inactivo';

interface UsuarioVista {
  id: number;
  nombre: string;
  correo: string;
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
export class UsuariosComponent {
  usuarios: UsuarioVista[] = [
    { id: 1, nombre: 'Ana Martínez', correo: 'ana.martinez@example.com', rol: 'Administrador', estado: 'Activo', fechaAlta: '12/06/2026' },
    { id: 2, nombre: 'Carlos López', correo: 'carlos.lopez@example.com', rol: 'Usuario', estado: 'Activo', fechaAlta: '03/07/2026' },
    { id: 3, nombre: 'María Fernández', correo: 'maria.fernandez@example.com', rol: 'Usuario', estado: 'Inactivo', fechaAlta: '18/08/2026' }
  ];

  busqueda = '';
  filtroRol = '';
  filtroEstado = '';
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
    this.formulario = { nombre: usuario.nombre, correo: usuario.correo, rol: usuario.rol, estado: usuario.estado };
    this.errorFormulario = '';
    this.modalAbierto = true;
  }

  guardar(): void {
    const nombre = this.formulario.nombre.trim();
    const correo = this.formulario.correo.trim().toLocaleLowerCase();
    if (!nombre || !correo) {
      this.errorFormulario = 'Completa el nombre y el correo electrónico.';
      return;
    }
    const correoEnUso = this.usuarios.some((usuario) => usuario.correo.toLocaleLowerCase() === correo && usuario.id !== this.editandoId);
    if (correoEnUso) {
      this.errorFormulario = 'Ya existe un usuario con ese correo.';
      return;
    }

    if (this.editandoId !== null) {
      this.usuarios = this.usuarios.map((usuario) => usuario.id === this.editandoId
        ? { ...usuario, ...this.formulario, nombre, correo }
        : usuario);
    } else {
      this.usuarios = [{
        id: Math.max(0, ...this.usuarios.map((usuario) => usuario.id)) + 1,
        ...this.formulario,
        nombre,
        correo,
        fechaAlta: new Date().toLocaleDateString('es-SV')
      }, ...this.usuarios];
    }
    this.modalAbierto = false;
  }

  solicitarEliminacion(usuario: UsuarioVista): void {
    this.eliminandoId = usuario.id;
    this.modalEliminarAbierto = true;
  }

  eliminar(): void {
    if (this.eliminandoId === null) return;
    this.usuarios = this.usuarios.filter((usuario) => usuario.id !== this.eliminandoId);
    this.modalEliminarAbierto = false;
    this.eliminandoId = null;
  }

  cancelarModal(): void { this.modalAbierto = false; }
  cancelarEliminacion(): void { this.modalEliminarAbierto = false; this.eliminandoId = null; }

  private formularioVacio(): { nombre: string; correo: string; rol: RolUsuario; estado: EstadoUsuario } {
    return { nombre: '', correo: '', rol: 'Usuario', estado: 'Activo' };
  }
}
