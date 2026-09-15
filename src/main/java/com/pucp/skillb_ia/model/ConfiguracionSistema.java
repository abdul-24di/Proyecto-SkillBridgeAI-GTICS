package com.pucp.skillb_ia.model;

import jakarta.persistence.*;

// Clave-valor genérico para los parámetros globales del Admin (Historia de
// Configuración de parámetros globales, Épica 5). Seed inicial en el schema:
// MAX_ASIGNACIONES_POR_COLABORADOR, TOPE_HORAS_EXTRA_BONO (A16),
// NOMBRE_ORGANIZACION.
@Entity
@Table(name = "configuracion_sistema")
public class ConfiguracionSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String clave;

    @Column(nullable = false, length = 255)
    private String valor;

    @Column(length = 300)
    private String descripcion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }

    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
