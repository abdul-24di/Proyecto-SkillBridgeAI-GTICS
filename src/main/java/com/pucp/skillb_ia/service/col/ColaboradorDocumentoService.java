package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.model.Documento;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaDocumento;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.DocumentoRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
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

@Service
public class ColaboradorDocumentoService {

    private static final long TAMANO_MAXIMO_DOCUMENTO_BYTES = 20L * 1024 * 1024; // 20 MB

    //Extensiones permitidas que estan agrupadas por la categoría que le vamos a asignar automáticamente
    private static final Set<String> EXTENSIONES_PDF = Set.of("pdf");
    private static final Set<String> EXTENSIONES_EXCEL = Set.of("xls", "xlsx", "csv");
    private static final Set<String> EXTENSIONES_WORD = Set.of("doc", "docx");
    private static final Set<String> EXTENSIONES_IMAGEN = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> EXTENSIONES_DISENO = Set.of("fig", "psd", "ai", "sketch", "xd");

    private static final Set<String> EXTENSIONES_PERMITIDAS = construirExtensionesPermitidas();

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
    private final String uploadDir;

    public ColaboradorDocumentoService(DocumentoRepository documentoRepository,
                                       AsignacionRepository asignacionRepository,
                                       ProyectoRepository proyectoRepository,
                                       AuditoriaService auditoriaService,
                                       @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.documentoRepository = documentoRepository;
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.auditoriaService = auditoriaService;
        this.uploadDir = uploadDir;
    }

    // ============================================================
    // LISTAMOS LOS DOCUMENTOS
    // ============================================================
    public List<Documento> listarDocumentos(Usuario colaborador, Long proyectoId, String categoriaFiltro, String busqueda) {
        Proyecto proyecto = validarAccesoProyecto(colaborador, proyectoId);
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

    //Validamos que el colaborador solo pueda ver o subir documentos de proyectos donde tiene asignación ACTIVA
    private Proyecto validarAccesoProyecto(Usuario colaborador, Long proyectoId) {
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
    @Transactional
    public void subirDocumento(Usuario colaborador, Long proyectoId, MultipartFile archivo) {
        Proyecto proyecto = validarAccesoProyecto(colaborador, proyectoId);

        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Selecciona un archivo para subir.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_DOCUMENTO_BYTES) {
            throw new IllegalArgumentException("El archivo no puede superar los 20 MB.");
        }

        String nombreOriginal = archivo.getOriginalFilename();
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            throw new IllegalArgumentException("El archivo no tiene un nombre válido.");
        }

        String extension = obtenerExtension(nombreOriginal);
        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Tipo de archivo no permitido. Formatos aceptados: PDF, Word, Excel, imágenes y archivos de diseño.");
        }

        CategoriaDocumento categoria = determinarCategoria(extension);

        try {
            Path carpeta = Path.of(uploadDir, "documentos", "proyecto-" + proyecto.getId());
            Files.createDirectories(carpeta);
            String nombreArchivo = "doc-" + UUID.randomUUID() + "." + extension;
            Path destino = carpeta.resolve(nombreArchivo);
            Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

            Documento documento = new Documento();
            documento.setProyecto(proyecto);
            documento.setSubidoPor(colaborador);
            documento.setNombre(nombreOriginal);
            documento.setCategoria(categoria);
            documento.setArchivoUrl("/uploads/documentos/proyecto-" + proyecto.getId() + "/" + nombreArchivo);
            documentoRepository.save(documento);

            auditoriaService.registrar(colaborador, "SUBIR_DOCUMENTO", "DOCUMENTO", documento.getId(),
                    "Subió el documento \"" + nombreOriginal + "\" al proyecto \"" + proyecto.getNombre() + "\".");
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el documento.", e);
        }
    }

    private String obtenerExtension(String nombreArchivo) {
        int puntoIndex = nombreArchivo.lastIndexOf('.');
        if (puntoIndex == -1 || puntoIndex == nombreArchivo.length() - 1) {
            throw new IllegalArgumentException("El archivo debe tener una extensión válida.");
        }
        return nombreArchivo.substring(puntoIndex + 1).toLowerCase();
    }

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