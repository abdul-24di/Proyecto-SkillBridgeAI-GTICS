package com.pucp.skillb_ia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

// Cliente HTTP para consumir servicios web externos (clase 10.1: consumo con RestTemplate).
// Lo usa DniService para consultar el DNI en la API de verifica.id.
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Tiempos máximos: si la API externa no responde, el registro no se queda esperando.
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5000);
        fabrica.setReadTimeout(8000);
        return new RestTemplate(fabrica);
    }
}
