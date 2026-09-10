package com.pucp.skillb_ia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    //Buscamos la propiedad de la carpeta en el archivo application.properties. Si no existe, usamos uploads por defecto.
    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        //Convertimos la ruta de la carpeta de subida en una URI absoluta representada como String
        String location = Path.of(uploadDir).toAbsolutePath().toUri().toString();

        // Accedemos mediante /uploads/ a los archivos guardados físicamente en la carpeta indicada por location
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);

    }


}
