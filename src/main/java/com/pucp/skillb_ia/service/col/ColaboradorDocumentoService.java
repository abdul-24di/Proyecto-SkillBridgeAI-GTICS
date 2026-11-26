package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.model.Documento;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaDocumento;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.DocumentoRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lógica de los documentos de un proyecto desde el lado del colaborador: listarlos con filtros,
 * subir uno nuevo y eliminarlo.
 * <p>
 * Distingue dos niveles de acceso: para <b>ver</b> documentos basta con tener o haber tenido una
 * asignación {@code ACTIVA} o {@code FINALIZADA} en el proyecto; para <b>subir</b> se exige una
 * asignación {@code ACTIVA} (un proyecto terminado ya no recibe archivos nuevos). La categoría
 * del documento se asigna sola según la extensión del archivo.
 */
@Service
public class ColaboradorDocumentoService {

    /** Tamaño máximo de un documento: 20 MB. */
    private static final long TAMANO_MAXIMO_DOCUMENTO_BYTES = 20L * 1024 * 1024; // 20 MB

    /**
     * Extensiones permitidas, agrupadas por la categoría que se le asigna automáticamente al
     * documento: PDF, Excel (xls, xlsx, csv), Word (doc, docx), imagen (jpg, jpeg, png, gif,
     * webp) y diseño (fig, psd, ai, sketch, xd).
     */
    private static final Set<String> EXTENSIONES_PDF = Set.of("pdf");
    private static final Set<String> EXTENSIONES_EXCEL = Set.of("xls", "xlsx", "csv");
    private static final Set<String> EXTENSIONES_WORD = Set.of("doc", "docx");
    private static final Set<String> EXTENSIONES_IMAGEN = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> EXTENSIONES_DISENO = Set.of("fig", "psd", "ai", "sketch", "xd");

    /** Unión de todas las extensiones de arriba: lo único que acepta {@link #validarArchivo}. */
    private static final Set<String> EXTENSIONES_PERMITIDAS = construirExtensionesPermitidas();

    /**
     * Junta las extensiones de todas las categorías en un solo conjunto.
     *
     * @return conjunto con todas las extensiones permitidas
     */
    private static Set<String> construirExtensionesPermitidas() {
        Set<String> extensiones = new HashSet<>();
        extensiones.addAll(EXTENSIONES_PDF);
        extensiones.addAll(EXTENSIONES_EXCEL);
        extensiones.addAll(EXTENSIONES_WORD);
        extensiones.addAll(EXTENSIONES_IMAGEN);
        extensiones.addAll(EXTENSIONES_DISENO);
        return extensiones;
    }

    private final DocumentoRepository documentoRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;

    private final AuditoriaService auditoriaService;

    private final NotificacionService notificacionService;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;

    /**
     * Inyecta por constructor las dependencias del service.
     *
     * @param documentoRepository acceso a documentos
     * @param asignacionRepository acceso a asignaciones (para validar el acceso al proyecto)
     * @param proyectoRepository acceso a proyectos
     * @param auditoriaService registro en el log de auditoría
     * @param notificacionService envío de notificaciones
     * @param archivoAlmacenamientoService guardado de archivos
     */
    public ColaboradorDocumentoService(DocumentoRepository documentoRepository,
                                       AsignacionRepository asignacionRepository,
                                       ProyectoRepository proyectoRepository,
                                       AuditoriaService auditoriaService,
                                       NotificacionService notificacionService,
                                       ArchivoAlmacenamientoService archivoAlmacenamientoService) {
        this.documentoRepository = documentoRepository;
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
    }

    // ============================================================
    // LISTAMOS LOS DOCUMENTOS
    // ============================================================
    /**
     * Lista los documentos activos de un proyecto, del más reciente al más antiguo, aplicando
     * los filtros opcionales.
     * <ul>
     *   <li>Categoría: coincidencia exacta con el nombre de la categoría ({@code PDF}, {@code EXCEL}, etc.).</li>
     *   <li>Búsqueda: texto contenido en el nombre del archivo, sin distinguir mayúsculas.</li>
     * </ul>
     *
     * @param colaborador colaborador que consulta
     * @param proyectoId proyecto consultado
     * @param categoriaFiltro categoría a filtrar; vacío o {@code null} para no filtrar
     * @param busqueda texto a buscar en el nombre; vacío o {@code null} para no filtrar
     * @return documentos que cumplen los filtros
     * @throws IllegalArgumentException si el proyecto no existe o no tiene acceso
     */
    public List<Documento> listarDocumentos(Usuario colaborador, Long proyectoId, String categoriaFiltro, String busqueda) {
        Proyecto proyecto = validarAccesoLectura(colaborador, proyectoId);
        List<Documento> todos = documentoRepository.findByProyectoAndActivoTrueOrderByFechaCreacionDesc(proyecto);

        List<Documento> resultado = new ArrayList<>();
        String textoBusqueda = (busqueda == null) ? "" : busqueda.trim().toLowerCase();

        for (Documento documento : todos) {

            //Filtramos por categoria en caso de que el colaborador eligiera una en el select
            if (categoriaFiltro != null && !categoriaFiltro.isBlank()
                    && !documento.getCategoria().name().equals(categoriaFiltro)) {
                continue;
            }

            //Filtramos por texto de busqueda sobre el nombre del archivo
            if (!textoBusqueda.isEmpty()) {
                String nombreDocumento = documento.getNombre() == null ? "" : documento.getNombre().toLowerCase();
                if (!nombreDocumento.contains(textoBusqueda)) {
                    continue;
                }
            }

            resultado.add(documento);
        }
        return resultado;
    }

    /**
     * Comprueba que el colaborador pueda <b>ver</b> los documentos: debe tener o haber tenido
     * una asignación {@code ACTIVA} o {@code FINALIZADA} en el proyecto.
     *
     * @param colaborador colaborador que consulta
     * @param proyectoId proyecto consultado
     * @return el proyecto
     * @throws IllegalArgumentException si el proyecto no existe o no tiene acceso
     */
    private Proyecto validarAccesoLectura(Usuario colaborador, Long proyectoId) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("El proyecto no existe."));

        boolean tieneAcceso = asignacionRepository.tieneAccesoVigente(proyecto, colaborador);
        if (!tieneAcceso) {
            throw new IllegalArgumentException("No tienes acceso a los documentos de este proyecto.");
        }


        return proyecto;
    }

    /**
     * Comprueba que el colaborador pueda <b>subir</b> documentos: se exige asignación
     * {@code ACTIVA}, porque un proyecto finalizado ya no recibe archivos nuevos.
     *
     * @param colaborador colaborador que sube
     * @param proyectoId proyecto destino
     * @return el proyecto
     * @throws IllegalArgumentException si el proyecto no existe o no tiene asignación activa
     */
    private Proyecto validarAccesoEscritura(Usuario colaborador, Long proyectoId) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("El proyecto no existe."));

        boolean tieneAsignacionActiva = asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                proyecto, colaborador, EstadoAsignacion.ACTIVA);
        if (!tieneAsignacionActiva) {
            throw new IllegalArgumentException("No tienes una asignación activa en este proyecto.");
        }
        return proyecto;
    }

    // ============================================================
    // SUBIDA DEL DOCUMENTO
    // ============================================================
    /**
     * Valida el archivo a subir. No lanza excepción: devuelve el mensaje de error para que el
     * controller lo muestre junto al campo.
     * <p>
     * Reglas: debe existir, pesar como máximo 20 MB, tener nombre con extensión y que la
     * extensión sea una de las permitidas (PDF, Word, Excel, imágenes y archivos de diseño).
     *
     * @param archivo archivo seleccionado
     * @return el mensaje de error, o {@code null} si el archivo es válido
     */
    public String validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return "Selecciona un archivo para subir.";
        }
        if (archivo.getSize() > TAMANO_MAXIMO_DOCUMENTO_BYTES) {
            return "El archivo no puede superar los 20 MB.";
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || nombre.isBlank()) {
            return "El archivo no tiene un nombre válido.";
        }
        int punto = nombre.lastIndexOf('.');
        if (punto == -1 || punto == nombre.length() - 1) {
            return "El archivo debe tener una extensión válida.";
        }
        String extension = nombre.substring(punto + 1).toLowerCase();
        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            return "Tipo de archivo no permitido. Formatos aceptados: PDF, Word, Excel, imágenes y archivos de diseño.";
        }
        return null;
    }

    /**
     * Sube un documento a un proyecto.
     * <p>
     * Exige asignación {@code ACTIVA} y un archivo válido ({@link #validarArchivo}). La categoría
     * se deduce de la extensión; el archivo se guarda con nombre único
     * ({@code doc-<uuid>.<ext>}) en la carpeta {@code documentos/proyecto-<id>}, pero el documento
     * conserva su <b>nombre original</b> para mostrarlo. Registra auditoría y notifica al PM,
     * salvo que quien sube sea el propio PM.
     *
     * @param colaborador colaborador que sube el documento
     * @param proyectoId proyecto destino
     * @param archivo archivo a subir
     * @throws IllegalArgumentException si no tiene asignación activa o el archivo no es válido
     */
    @Transactional
    public void subirDocumento(Usuario colaborador, Long proyectoId, MultipartFile archivo) {
        Proyecto proyecto = validarAccesoEscritura(colaborador, proyectoId);

        String errorArchivo = validarArchivo(archivo);
        if (errorArchivo != null) {
            throw new IllegalArgumentException(errorArchivo);
        }

        String nombreOriginal = archivo.getOriginalFilename();
        String extension = obtenerExtension(nombreOriginal);

        CategoriaDocumento categoria = determinarCategoria(extension);

        String nombreArchivo = "doc-" + UUID.randomUUID() + "." + extension;
        String carpeta = "documentos/proyecto-" + proyecto.getId();
        String archivoUrl = archivoAlmacenamientoService.guardar(archivo, carpeta, nombreArchivo);

        Documento documento = new Documento();
        documento.setProyecto(proyecto);
        documento.setSubidoPor(colaborador);
        documento.setNombre(nombreOriginal);
        documento.setCategoria(categoria);
        documento.setArchivoUrl(archivoUrl);
        documentoRepository.save(documento);

        auditoriaService.registrar(colaborador, "SUBIR_DOCUMENTO", "DOCUMENTO", documento.getId(),
                "Subió el documento \"" + nombreOriginal + "\" al proyecto \"" + proyecto.getNombre() + "\".");

        //No notificamos al PM si el que sube el documento es el propio PM
        if (!proyecto.getPm().getId().equals(colaborador.getId())) {
            notificacionService.crear(proyecto.getPm(), "DOCUMENTO_SUBIDO", CategoriaNotificacion.PROYECTO,
                    "Nuevo documento en tu proyecto",
                    colaborador.getNombre() + " " + colaborador.getApellido()
                            + " subió \"" + nombreOriginal + "\" a \"" + proyecto.getNombre() + "\".",
                    "DOCUMENTO", documento.getId());
        }
    }

    /**
     * Obtiene la extensión de un nombre de archivo, en minúsculas.
     *
     * @param nombreArchivo nombre del archivo
     * @return la extensión sin punto
     * @throws IllegalArgumentException si no tiene extensión
     */
    private String obtenerExtension(String nombreArchivo) {
        int puntoIndex = nombreArchivo.lastIndexOf('.');
        if (puntoIndex == -1 || puntoIndex == nombreArchivo.length() - 1) {
            throw new IllegalArgumentException("El archivo debe tener una extensión válida.");
        }
        return nombreArchivo.substring(puntoIndex + 1).toLowerCase();
    }

    /**
     * Deduce la categoría del documento según su extensión.
     *
     * @param extension extensión en minúsculas
     * @return {@code PDF}, {@code EXCEL}, {@code WORD}, {@code IMAGEN} o {@code DISENO};
     *         {@code OTRO} si no coincide con ninguna
     */
    private CategoriaDocumento determinarCategoria(String extension) {
        if (EXTENSIONES_PDF.contains(extension)) {
            return CategoriaDocumento.PDF;
        } else if (EXTENSIONES_EXCEL.contains(extension)) {
            return CategoriaDocumento.EXCEL;
        } else if (EXTENSIONES_WORD.contains(extension)) {
            return CategoriaDocumento.WORD;
        } else if (EXTENSIONES_IMAGEN.contains(extension)) {
            return CategoriaDocumento.IMAGEN;
        } else if (EXTENSIONES_DISENO.contains(extension)) {
            return CategoriaDocumento.DISENO;
        }
        return CategoriaDocumento.OTRO;
    }

    // ============================================================
    // ELIMINAMOS DOCUMENTO
    // ============================================================
    /**
     * Elimina un documento. Solo puede hacerlo quien lo subió o un Administrador. Es un borrado
     * lógico: el documento queda inactivo y deja de listarse, pero el archivo no se borra del
     * almacenamiento. Registra auditoría.
     *
     * @param colaborador usuario que elimina
     * @param documentoId documento a eliminar
     * @throws IllegalArgumentException si no existe, ya fue eliminado o no es quien lo subió ni Administrador
     */
    @Transactional
    public void eliminarDocumento(Usuario colaborador, Long documentoId) {
        Documento documento = documentoRepository.findByIdAndActivoTrue(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("El documento no existe o ya fue eliminado."));

        boolean esQuienLoSubio = documento.getSubidoPor().getId().equals(colaborador.getId());
        boolean esAdministrador = colaborador.getRol().getNombre().equals("ADMINISTRADOR");

        if (!esQuienLoSubio && !esAdministrador) {
            throw new IllegalArgumentException("Solo quien subió el documento (o un Administrador) puede eliminarlo.");
        }

        documento.setActivo(false);
        documentoRepository.save(documento);

        auditoriaService.registrar(colaborador, "ELIMINAR_DOCUMENTO", "DOCUMENTO", documentoId,
                "Eliminó el documento \"" + documento.getNombre() + "\" del proyecto \""
                        + documento.getProyecto().getNombre() + "\".");
    }
}