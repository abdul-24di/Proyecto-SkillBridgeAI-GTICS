package com.pucp.skillb_ia.dto.auth;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// Presenta un archivo temporal como un MultipartFile, para reutilizar sin cambios los mismos métodos de
// ColaboradorPerfilService que reciben las subidas normales del perfil.
public class ArchivoTemporalMultipart implements MultipartFile {

    private final ArchivoPendiente archivo;
    private final Path ruta;

    public ArchivoTemporalMultipart(ArchivoPendiente archivo, Path ruta) {
        this.archivo = archivo;
        this.ruta = ruta;
    }

    @Override public String getName() { return archivo.id(); }
    @Override public String getOriginalFilename() { return archivo.nombre(); }
    @Override public String getContentType() { return archivo.tipo(); }
    @Override public boolean isEmpty() { return archivo.tamano() == 0; }
    @Override public long getSize() { return archivo.tamano(); }
    @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(ruta); }
    @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(ruta); }

    @Override
    public void transferTo(File destino) throws IOException {
        Files.copy(ruta, destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
