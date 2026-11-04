package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.auth.ArchivoPendiente;
import com.pucp.skillb_ia.dto.auth.ArchivoTemporalMultipart;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// El navegador no puede volver a llenar un <input type="file">, así que si el formulario de activación
// vuelve con errores el colaborador perdería sus archivos. Por eso cada archivo válido se guarda aquí hasta
// que el formulario pase: en disco van los bytes y en la sesión HTTP solo sus datos (la sesión vive en MySQL
// y no debe cargar archivos). El formulario solo envía el id: el archivo se busca en la sesión de quien lo
// subió, así que no se puede pedir el archivo de otra persona ni una ruta del servidor.
@Service
public class PreRegistroArchivosService {

    private static final Logger log = LoggerFactory.getLogger(PreRegistroArchivosService.class);
    private static final String ATRIBUTO_SESION = "preRegistroArchivos";
    private static final Path CARPETA = Path.of(System.getProperty("java.io.tmpdir"), "skillbridge-preregistro");
    private static final int MAX_ARCHIVOS_POR_SESION = 40; // CV + 10 certificados + 10 estudios, con reintentos
    private static final int HORAS_DE_VIDA = 24;

    @SuppressWarnings("unchecked")
    public Map<String, ArchivoPendiente> archivos(HttpSession session) {
        Object guardados = session.getAttribute(ATRIBUTO_SESION);
        return guardados == null ? new HashMap<>() : (Map<String, ArchivoPendiente>) guardados;
    }

    public ArchivoPendiente guardar(MultipartFile archivo, HttpSession session) throws IOException {
        Map<String, ArchivoPendiente> guardados = archivos(session);
        if (guardados.size() >= MAX_ARCHIVOS_POR_SESION) {
            throw new IllegalArgumentException("Subiste demasiados archivos. Vuelve a abrir el enlace de activación.");
        }
        borrarAbandonados();
        Files.createDirectories(CARPETA);

        String id = UUID.randomUUID().toString();
        try (var entrada = archivo.getInputStream()) {
            Files.copy(entrada, CARPETA.resolve(id), StandardCopyOption.REPLACE_EXISTING);
        }
        ArchivoPendiente pendiente = new ArchivoPendiente(id, nombreVisible(archivo.getOriginalFilename()),
                archivo.getContentType(), archivo.getSize());
        guardados.put(id, pendiente);
        // setAttribute otra vez: Spring Session solo guarda en la base de datos lo que se vuelve a asignar.
        session.setAttribute(ATRIBUTO_SESION, guardados);
        return pendiente;
    }

    // null si el id no pertenece a esta sesión.
    public MultipartFile abrir(String id, HttpSession session) {
        ArchivoPendiente pendiente = id == null ? null : archivos(session).get(id);
        return pendiente == null ? null : new ArchivoTemporalMultipart(pendiente, CARPETA.resolve(id));
    }

    // Al terminar el pre-registro: borra los archivos y los quita de la sesión.
    public void limpiar(HttpSession session) {
        archivos(session).keySet().forEach(id -> borrar(CARPETA.resolve(id)));
        session.removeAttribute(ATRIBUTO_SESION);
    }

    // Quien cierra el navegador a mitad del formulario deja archivos sin dueño.
    private void borrarAbandonados() throws IOException {
        if (!Files.isDirectory(CARPETA)) {
            return;
        }
        FileTime limite = FileTime.from(Instant.now().minus(HORAS_DE_VIDA, ChronoUnit.HOURS));
        try (DirectoryStream<Path> archivos = Files.newDirectoryStream(CARPETA)) {
            for (Path archivo : archivos) {
                if (Files.getLastModifiedTime(archivo).compareTo(limite) < 0) {
                    borrar(archivo);
                }
            }
        }
    }

    private void borrar(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            log.warn("No se pudo borrar el archivo temporal {}: {}", archivo.getFileName(), e.getMessage());
        }
    }

    private String nombreVisible(String original) {
        if (original == null || original.isBlank()) {
            return "archivo";
        }
        String nombre = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        return nombre.length() > 80 ? nombre.substring(0, 80) : nombre;
    }
}
