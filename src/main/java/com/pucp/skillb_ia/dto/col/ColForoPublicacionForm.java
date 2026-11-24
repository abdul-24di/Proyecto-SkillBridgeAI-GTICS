package com.pucp.skillb_ia.dto.col;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Formulario de una nueva publicación del colaborador en un foro.
 * <p>
 * Validaciones: foro obligatorio, título obligatorio (máx. 200 caracteres) y contenido
 * obligatorio (máx. 10000). Además el controller rechaza el contenido que solo tenga
 * espacios o etiquetas vacías del editor (por ejemplo {@code <p><br></p>}).
 * <p>
 * Si hay errores, el formulario se devuelve con el modal abierto y cada mensaje junto a su
 * campo. Sigue el mismo patrón que {@code PmForoPublicacionForm}. El campo {@code origen}
 * indica a dónde volver: {@code "foros"} (listado) o {@code "detalle"} (dentro de un foro).
 *
 * @see com.pucp.skillb_ia.controller.ColaboradorViewController#publicarForo
 */
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
