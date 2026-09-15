package com.pucp.skillb_ia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Sin servidor SMTP configurado todavía (no hay credenciales de correo en
// application.properties), así que por ahora "envía" logueando a consola.
// La interfaz pública ya queda lista: cuando se agregue spring-boot-starter-mail
// y las credenciales reales, solo hay que cambiar la implementación de estos
// dos métodos — nadie más en el código necesita cambiar.
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public void enviarActivacion(String correo, String enlaceActivacion) {
        log.info("[EMAIL] Activación de cuenta -> {} | enlace: {}", correo, enlaceActivacion);
    }

    public void enviarCodigoRecuperacion(String correo, String codigo) {
        log.info("[EMAIL] Código de recuperación -> {} | código: {}", correo, codigo);
    }
}
