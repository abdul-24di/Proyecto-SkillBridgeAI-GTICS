package com.pucp.skillb_ia.dto.pm;
import jakarta.validation.constraints.*;

public class PmPerfilDatosForm {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 50, message = "Mximo 50 caracteres")
    private String nombre;

    @Size(max = 50, message = "Mximo 50 caracteres")
    private String apellido;

    @Size(max = 50, message = "Mximo 50 caracteres")
    private String cargo;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
}
