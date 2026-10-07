package com.pucp.skillb_ia.dto.pm;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

// Solicitud de personal del PM al RM (TASK-058). Serializable: vuelve como flash tras un error.
// Los perfiles solo son obligatorios si el total supera la suma por habilidad (regla en PmSolicitudPersonalService).
public class PmSolicitudPersonalForm implements Serializable {
    @NotNull(message = "La cantidad de colaboradores es obligatoria.")
    @Positive(message = "La cantidad solicitada debe ser mayor que cero.")
    private Integer cantidad;

    // Clave: id de la habilidad requerida del proyecto; valor: colaboradores pedidos (vacío = 0).
    private Map<Long, @PositiveOrZero(message = "La cantidad por habilidad no puede ser negativa.") Integer>
            cantidadesPorHabilidad = new LinkedHashMap<>();

    @Size(max = 1000, message = "Los perfiles requeridos no pueden exceder 1000 caracteres.")
    private String perfiles;

    @Size(max = 1000, message = "El mensaje no puede exceder 1000 caracteres.")
    private String mensaje;

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public Map<Long, Integer> getCantidadesPorHabilidad() { return cantidadesPorHabilidad; }
    public void setCantidadesPorHabilidad(Map<Long, Integer> cantidadesPorHabilidad) {
        this.cantidadesPorHabilidad = cantidadesPorHabilidad == null ? new LinkedHashMap<>() : cantidadesPorHabilidad;
    }
    public String getPerfiles() { return perfiles; }
    public void setPerfiles(String perfiles) { this.perfiles = perfiles; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    // Valor que el modal vuelve a mostrar para una habilidad (0 si no se ingresó).
    public int cantidadPara(Long habilidadId) {
        Integer valor = cantidadesPorHabilidad.get(habilidadId);
        return valor == null || valor < 0 ? 0 : valor;
    }
}
