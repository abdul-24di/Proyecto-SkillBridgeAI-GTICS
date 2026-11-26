package com.pucp.skillb_ia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import com.pucp.skillb_ia.security.RegistroPendienteInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/** Configuración de Spring MVC: interceptor del pre-registro y carpeta de archivos subidos. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    //Buscamos la propiedad de la carpeta en el archivo application.properties. Si no existe, usamos uploads por defecto.
    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private final RegistroPendienteInterceptor registroPendienteInterceptor;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public WebMvcConfig(RegistroPendienteInterceptor registroPendienteInterceptor) {
        this.registroPendienteInterceptor = registroPendienteInterceptor;
    }

    /**
     * Registra el interceptor de los colaboradores sin aprobar.
     * <p>
     * Un colaborador con el pre-registro pendiente o rechazado solo puede ver /colaborador/registro.
     *
     * @param registry registro de Spring MVC
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(registroPendienteInterceptor)
                .addPathPatterns("/colaborador/**")
                .excludePathPatterns("/colaborador/registro", "/colaborador/registro/**");
    }

    /**
     * Publica en {@code /uploads/**} los archivos guardados en disco.
     *
     * @param registry registro de Spring MVC
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        //Convertimos la ruta de la carpeta de subida en una URI absoluta representada como String
        String location = Path.of(uploadDir).toAbsolutePath().toUri().toString();

        // Accedemos mediante /uploads/ a los archivos guardados físicamente en la carpeta indicada por location
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);

    }


}
