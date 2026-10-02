package com.pucp.skillb_ia.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class ArchivoAlmacenamientoService {

    private static final Logger log = LoggerFactory.getLogger(ArchivoAlmacenamientoService.class);

    private final String tipoAlmacenamiento; //Guarda si usaremos "local" o "s3"
    private final String uploadDir;          //Nombre de la carpeta local
    private final String bucket;             //Nombre del bucket en AWS
    private final String urlBase;            //URL web base para cargar los archivos públicamente
    private final S3Client s3Client;         //El "control remoto" para enviar los comandos a AWS S3


    public ArchivoAlmacenamientoService(@Value("${app.storage.type:local}") String tipoAlmacenamiento,
                                        @Value("${app.upload-dir:uploads}") String uploadDir,
                                        @Value("${aws.s3.bucket:}") String bucket,
                                        @Value("${aws.s3.region:us-east-1}") String region,
                                        @Value("${aws.s3.url-base:}") String urlBaseManual,
                                        S3Client s3Client) { //Inyecta el s3Client inteligente que creamos en S3Config
        //.strip() quita espacios invisibles al inicio/final
        this.tipoAlmacenamiento = tipoAlmacenamiento == null ? "local" : tipoAlmacenamiento.strip();
        this.uploadDir = uploadDir == null ? "uploads" : uploadDir.strip();
        this.bucket = bucket == null ? "" : bucket.strip();

        //aws.s3.url-base ya NO es obligatorio, ya que si no lo configuramos
        //armamos nosotros mismos la URL pública a partir del
        //bucket y la región. Así nadie puede volver a escribirla incompleta o
        //con un espacio de más
        String urlBaseLimpia = urlBaseManual == null ? "" : urlBaseManual.strip();
        this.urlBase = urlBaseLimpia.isBlank()
                ? "https://" + this.bucket + ".s3." + region.strip() + ".amazonaws.com"
                : quitarBarraFinal(urlBaseLimpia);

        this.s3Client = s3Client;
    }

    //Por si alguien SÍ configura la url-base a mano y la deja terminando en
    //"/", se la limpiamos para no generar URLs con doble barra.
    private String quitarBarraFinal(String valor) {
        return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor;
    }


    public String guardar(MultipartFile archivo, String carpeta, String nombreArchivo) {

        //Si el tipo de la configuración es igual a s3, entonces ejecutamos el metodo para subirlo a Amazon
        if ("s3".equalsIgnoreCase(tipoAlmacenamiento)) {
            return guardarEnS3(archivo, carpeta, nombreArchivo);
        }
        //Si no no es s3 o dice "local", entonces guardamos el archivo directamente en el disco de la computadora
        return guardarEnDiscoLocal(archivo, carpeta, nombreArchivo);
    }

    //Subimos los archivos a la nube de AWS S3
    private String guardarEnS3(MultipartFile archivo, String carpeta, String nombreArchivo) {
        //Combinamos la carpeta y el nombre para crear la ruta dentro de S3
        String key = carpeta + "/" + nombreArchivo;

        try {
            //Configuramos la solicitud de subida indicando el destino, la ruta interna y el tipo de archivo
            PutObjectRequest solicitud = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(archivo.getContentType()) //Permite que el navegador abra el archivo en vez de descargarlo solo
                    .build();

            //Envíamos físicamente el archivo convirtiendo los datos a un flujo de lectura (InputStream) compatible con AWS
            s3Client.putObject(solicitud, RequestBody.fromInputStream(archivo.getInputStream(), archivo.getSize()));

        } catch (IOException e) {
            //Error leyendo el archivo que subió el usuario (poco común)
            throw new IllegalArgumentException("No se pudo leer el archivo. Inténtalo nuevamente.", e);
        } catch (SdkException e) {
            //El mensaje crudo del SDK es larguísimo y solo tiene sentido para un
            //desarrollador, así que lo mandamos completo al log de la consola y
            //al usuario le mostramos una versión corta según el tipo de error.
            log.error("Fallo al subir archivo a S3 (bucket={})", bucket, e);
            throw new IllegalArgumentException(mensajeAmigableS3(e), e);
        }

        //Retornamos la URL web completa lista para el navegador
        return urlBase + "/" + key;
    }

    //Mostramos mensajes de error más amigables para el colaborador
    private String mensajeAmigableS3(SdkException e) {
        String detalle = e.getMessage() == null ? "" : e.getMessage();

        if (detalle.contains("Unable to load credentials")) {
            return "El servidor todavía no está conectado a AWS. Avisa al equipo técnico.";
        }
        if (detalle.contains("ExpiredToken") || (detalle.contains("token") && detalle.contains("expired"))) {
            return "Las credenciales de AWS vencieron y hay que renovarlas.";
        }
        if (detalle.contains("AccessDenied") || detalle.contains("Access Denied")) {
            return "No tenemos permiso para subir archivos a S3 en este momento.";
        }
        if (detalle.contains("NoSuchBucket")) {
            return "El bucket de S3 no existe o está mal configurado.";
        }
        return "No se pudo subir el archivo. Intenta de nuevo en un momento.";
    }

    //Guardamos los archivos de manera local
    private String guardarEnDiscoLocal(MultipartFile archivo, String carpeta, String nombreArchivo) {
        try {
            //Definimos la ruta en la computadora uniendo la carpeta principal con la subcarpeta
            Path carpetaDestino = Path.of(uploadDir, carpeta);

            //Si la estructura de carpetas no existe en el disco, la crea automáticamente
            Files.createDirectories(carpetaDestino);

            //Copiamos los bytes del archivo enviado hacia la ruta de destino, reemplazando el archivo si ya existiera uno igual
            Files.copy(archivo.getInputStream(), carpetaDestino.resolve(nombreArchivo), StandardCopyOption.REPLACE_EXISTING);


        } catch (IOException e) {
            //Si ocurre un error de lectura/escritura en el disco, arrojamos este error
            throw new IllegalArgumentException("No se pudo guardar el archivo. Inténtalo nuevamente.", e);
        }
        //Retornamos la ruta relativa local para que el backend sepa dónde buscarlo en el disco
        return "/uploads/" + carpeta + "/" + nombreArchivo;
    }
}