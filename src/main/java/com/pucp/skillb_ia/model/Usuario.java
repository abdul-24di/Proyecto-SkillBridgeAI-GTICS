package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Tabla única para los 4 roles (se eliminó la tabla `colaborador` separada —
// ver consideraciones_bd_v4.md Parte 1.1). IMPORTANTE: la BD ya NO puede
// garantizar que solo un COLABORADOR tenga asignaciones/certificados/etc. —
// esa validación de rol es responsabilidad exclusiva del service de Java.
//
// Los campos de la sección "exclusivos de colaborador" quedan NULL para
// PM/RM/Admin — es esperado, no un dato faltante (consideraciones_bd_v4.md,
// Parte 1.2).
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String correo;

    // NULL hasta que el usuario activa su cuenta (Historia A6) y define su propia
    // contraseña — al crearlo, el Admin solo asigna correo y rol (A5).
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(length = 150)
    private String nombre;

    @Column(length = 150)
    private String apellido;

    @Column(length = 20)
    private String telefono;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    // --- Campos exclusivos de colaborador (NULL para los demás roles) ---

    // Catálogo administrado por el Admin (sin cascade: guardar un usuario nunca
    // debe crear ni modificar un cargo). El sueldo_base se deriva de cargo + nivel.
    // EAGER porque las vistas leen el cargo del usuario de la sesión (fuera de la
    // sesión de Hibernate) y el catálogo es pequeño.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cargo_id")
    private Cargo cargo;

    @Column(name = "horas_contratadas_semana", precision = 5, scale = 2)
    private BigDecimal horasContratadasSemana;

    @Column(name = "horas_disponibles", precision = 5, scale = 2)
    private BigDecimal horasDisponibles;

    @Column(name = "anios_experiencia", precision = 4, scale = 1)
    private BigDecimal aniosExperiencia;

    // Solo el propio colaborador y el Administrador pueden ver este campo (C7) —
    // esa restricción se aplica en el service/controller, no aquí.
    @Column(name = "sueldo_base", precision = 10, scale = 2)
    private BigDecimal sueldoBase;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_experiencia", length = 20)
    private NivelExperiencia nivelExperiencia;

    @Column(name = "fecha_contratacion")
    private LocalDate fechaContratacion;

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public Cargo getCargo() { return cargo; }
    public void setCargo(Cargo cargo) { this.cargo = cargo; }

    public BigDecimal getHorasContratadasSemana() { return horasContratadasSemana; }
    public void setHorasContratadasSemana(BigDecimal horasContratadasSemana) { this.horasContratadasSemana = horasContratadasSemana; }

    public BigDecimal getHorasDisponibles() { return horasDisponibles; }
    public void setHorasDisponibles(BigDecimal horasDisponibles) { this.horasDisponibles = horasDisponibles; }

    public BigDecimal getAniosExperiencia() { return aniosExperiencia; }
    public void setAniosExperiencia(BigDecimal aniosExperiencia) { this.aniosExperiencia = aniosExperiencia; }

    public BigDecimal getSueldoBase() { return sueldoBase; }
    public void setSueldoBase(BigDecimal sueldoBase) { this.sueldoBase = sueldoBase; }

    public NivelExperiencia getNivelExperiencia() { return nivelExperiencia; }
    public void setNivelExperiencia(NivelExperiencia nivelExperiencia) { this.nivelExperiencia = nivelExperiencia; }

    public LocalDate getFechaContratacion() { return fechaContratacion; }
    public void setFechaContratacion(LocalDate fechaContratacion) { this.fechaContratacion = fechaContratacion; }
}


