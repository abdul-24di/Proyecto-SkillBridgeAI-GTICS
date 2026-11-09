package com.pucp.skillb_ia.dto.col;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

// Nueva publicación del colaborador en un foro. Mismo patrón que PmForoPublicacionForm: si hay errores,
// el formulario vuelve con el modal abierto y cada mensaje junto a su campo.
public class ColForoPublicacionForm implements Serializable {

    @NotNull(message = "Elige el foro donde quieres publicar.")
    private Long foroId;

    @NotBlank(message = "El título de la publicación es obligatorio.")
    @Size(max = 200, message = "El título no puede superar los 200 caracteres.")
    private String titulo;

    @NotBlank(message = "El contenido de la publicación es obligatorio.")
    @Size(max = 10000, message = "El contenido no puede superar los 10000 caracteres.")
    private String contenido;

    private Long etiquetaId;

    // "foros" (listado) o "detalle" (dentro de un foro): a dónde volver si hay errores.
    private String origen;

    public Long getForoId() { return foroId; }
    public void setForoId(Long foroId) { this.foroId = foroId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public Long getEtiquetaId() { return etiquetaId; }
    public void setEtiquetaId(Long etiquetaId) { this.etiquetaId = etiquetaId; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
}
