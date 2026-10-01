package com.pucp.skillb_ia.model;

import jakarta.persistence.*;

import java.io.Serializable;

// Tabla de 4 filas fijas (ADMINISTRADOR, PROJECT_MANAGER, RESOURCE_MANAGER,
// COLABORADOR), no un catálogo gestionable por el Admin (consideraciones_bd_v4.md,
// Parte 1.3). No hay historia de "crear nuevo rol".
@Entity
@Table(name = "rol")
// Serializable: Usuario.rol viaja dentro de la sesión HTTP persistida (Spring Session).
public class Rol implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String nombre;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
