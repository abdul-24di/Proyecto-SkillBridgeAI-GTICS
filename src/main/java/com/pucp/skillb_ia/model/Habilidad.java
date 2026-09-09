package com.pucp.skillb_ia.model;

import jakarta.persistence.*;

// Catálogo oficial de habilidades gestionado por el Admin (Épica 5) — el
// colaborador elige de aquí, nunca escribe texto libre, para estandarizar
// los términos de toda la organización y evitar duplicados.
@Entity
@Table(name = "habilidad", uniqueConstraints = @UniqueConstraint(columnNames = {"nombre", "categoria_id"}))
public class Habilidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaHabilidad categoria;

    @Column(nullable = false)
    private boolean activa = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public CategoriaHabilidad getCategoria() { return categoria; }
    public void setCategoria(CategoriaHabilidad categoria) { this.categoria = categoria; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}
