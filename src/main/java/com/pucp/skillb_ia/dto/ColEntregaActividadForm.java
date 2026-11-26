package com.pucp.skillb_ia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;

/**
 * Formulario con el que el colaborador marca una actividad como "lista para revisión".
 * <p>
 * Campos:
 * <ul>
 *   <li><b>comentario:</b> obligatorio, máximo 300 caracteres. Se valida con Jakarta
 *       Validation ({@code @Valid}) en el controller.</li>
 *   <li><b>evidencia:</b> obligatoria. Debe ser un archivo PDF, JPG o PNG de hasta 10 MB.
 *       No se valida con anotaciones, porque {@code MultipartFile} no se puede validar de
 *       forma fiable con ellas; el controller la verifica con
 *       {@code ColaboradorProyectoService#validarEvidencia} y registra el error como
 *       {@code FieldError} sobre el campo {@code evidencia}.</li>
 * </ul>
 * <p>
 * La evidencia es {@code transient} porque la clase es {@link java.io.Serializable} y
 * {@code MultipartFile} no lo es. Por eso, al redirigir con errores, el controller la pone
 * en {@code null} antes de guardar el formulario como flash attribute: el colaborador debe
 * volver a adjuntar el archivo.
 *
 * @see com.pucp.skillb_ia.controller.ColaboradorViewController#marcarActividadListaParaRevision
 * @see com.pucp.skillb_ia.service.col.ColaboradorProyectoService#validarEvidencia
 */
public class ColEntregaActividadForm implements Serializable {

    @NotBlank(message = "Debes escribir un comentario para marcar la actividad como lista.")
    @Size(max = 300, message = "El comentario no puede superar los 300 caracteres.")
    private String comentario;


    private transient MultipartFile evidencia;

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public MultipartFile getEvidencia() { return evidencia; }
    public void setEvidencia(MultipartFile evidencia) { this.evidencia = evidencia; }
}