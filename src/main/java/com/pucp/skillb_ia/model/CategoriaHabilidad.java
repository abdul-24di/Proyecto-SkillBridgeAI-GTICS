package com.pucp.skillb_ia.model;

import jakarta.persistence.*;

// Agrupa las habilidades del catálogo (Lenguajes, Cloud, Soft Skills, etc.) —
// Historia "Catálogo de habilidades por categorías", Épica 5.
@Entity
@Table(name = "categoria_habilidad")
public class CategoriaHabilidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String nombre;

    @Column(length = 300)
    private String descripcion;

    @Column(nullable = false)
    private boolean activa = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}
