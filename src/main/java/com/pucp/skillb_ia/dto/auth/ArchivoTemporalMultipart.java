package com.pucp.skillb_ia.dto.auth;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Presenta un archivo temporal como un MultipartFile, para reutilizar sin cambios los mismos métodos de
 * ColaboradorPerfilService que reciben las subidas normales del perfil.
 */
public class ArchivoTemporalMultipart implements MultipartFile {

    private final ArchivoPendiente archivo;
    private final Path ruta;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public ArchivoTemporalMultipart(ArchivoPendiente archivo, Path ruta) {
        this.archivo = archivo;
        this.ruta = ruta;
    }

    /**
     * Abre el archivo para lectura.
     *
     * @return flujo de lectura
     * @throws IOException si no se puede abrir
     */
    /**
     * Lee todo el contenido.
     *
     * @return bytes del archivo
     * @throws IOException si no se puede leer
     */
    /**
     * Devuelve el tamaño en bytes.
     *
     * @return tamaño
     */
    /**
     * Indica si el archivo está vacío.
     *
     * @return {@code true} si no tiene contenido
     */
    /**
     * Devuelve el tipo de contenido.
     *
     * @return tipo de contenido
     */
    /**
     * Devuelve el nombre original.
     *
     * @return nombre del archivo
     */
    /**
     * Devuelve el id del archivo.
     *
     * @return id del archivo
     */
    @Override public String getName() { return archivo.id(); }
    @Override public String getOriginalFilename() { return archivo.nombre(); }
    @Override public String getContentType() { return archivo.tipo(); }
    @Override public boolean isEmpty() { return archivo.tamano() == 0; }
    @Override public long getSize() { return archivo.tamano(); }
    @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(ruta); }
    @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(ruta); }

    /**
     * Copia el archivo a otra ubicación.
     *
     * @param destino archivo de destino
     * @throws IOException si no se puede copiar
     */
    @Override
    public void transferTo(File destino) throws IOException {
        Files.copy(ruta, destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
