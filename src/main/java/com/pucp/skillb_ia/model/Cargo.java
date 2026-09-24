package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cargo")
public class Cargo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String nombre;

    @Column(name = "sueldo_junior", precision = 10, scale = 2)
    private BigDecimal sueldoJunior;

    @Column(name = "sueldo_semi_senior", precision = 10, scale = 2)
    private BigDecimal sueldoSemiSenior;

    @Column(name = "sueldo_senior", precision = 10, scale = 2)
    private BigDecimal sueldoSenior;

    @Column(nullable = false)
    private boolean activo = true;

    public Cargo() {}

    public Cargo(String nombre, BigDecimal sueldoJunior, BigDecimal sueldoSemiSenior, BigDecimal sueldoSenior) {
        this.nombre = nombre;
        this.sueldoJunior = sueldoJunior;
        this.sueldoSemiSenior = sueldoSemiSenior;
        this.sueldoSenior = sueldoSenior;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public BigDecimal getSueldoJunior() { return sueldoJunior; }
    public void setSueldoJunior(BigDecimal sueldoJunior) { this.sueldoJunior = sueldoJunior; }

    public BigDecimal getSueldoSemiSenior() { return sueldoSemiSenior; }
    public void setSueldoSemiSenior(BigDecimal sueldoSemiSenior) { this.sueldoSemiSenior = sueldoSemiSenior; }

    public BigDecimal getSueldoSenior() { return sueldoSenior; }
    public void setSueldoSenior(BigDecimal sueldoSenior) { this.sueldoSenior = sueldoSenior; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    // Sueldo que corresponde a un colaborador de este cargo según su nivel.
    // Devuelve null si no hay nivel o si la tarifa de ese nivel no está definida.
    public BigDecimal sueldoPara(NivelExperiencia nivel) {
        if (nivel == null) return null;
        return switch (nivel) {
            case JUNIOR -> sueldoJunior;
            case SEMI_SENIOR -> sueldoSemiSenior;
            case SENIOR -> sueldoSenior;
        };
    }
}
