package com.pucp.skillb_ia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// Envía correos reales por SMTP cuando MAIL_USERNAME/MAIL_PASSWORD están
// configurados (ver application.properties). Si no lo están (por ejemplo, un
// compañero sin credenciales de Gmail configuradas localmente), cae de
// vuelta a solo loguear a consola para no romper el flujo de activación.
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String remitente;
    private final String baseUrl;

    public EmailService(JavaMailSender mailSender,
                         @Value("${app.mail.from:}") String remitente,
                         @Value("${app.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        this.remitente = remitente;
        this.baseUrl = baseUrl;
    }

    public void enviarActivacion(String correo, String enlaceActivacion) {
        String enlaceCompleto = baseUrl + enlaceActivacion;
        String asunto = "Activa tu cuenta en SkillBridge AI";
        String cuerpo = "Hola,\n\n"
                + "Se creó una cuenta para ti en SkillBridge AI. Para activarla, completar tus datos (y subir tu CV si eres colaborador) y elegir tu contraseña, "
                + "entra al siguiente enlace (válido por 48 horas):\n\n"
                + enlaceCompleto + "\n\n"
                + "Si no esperabas este correo, puedes ignorarlo.\n\n"
                + "— SkillBridge AI";

        if (!enviar(correo, asunto, cuerpo)) {
            log.info("[EMAIL] Activación de cuenta -> {} | enlace: {}", correo, enlaceCompleto);
        }
    }

    // Se avisa por correo porque, mientras su registro está en revisión, el colaborador
    // no tiene el sistema abierto para ver la notificación.
    public void enviarRechazoRegistro(String correo, String motivo) {
        String asunto = "Tu registro en SkillBridge AI necesita correcciones";
        String cuerpo = "Hola,\n\n"
                + "El Administrador revisó tu registro y lo devolvió con la siguiente observación:\n\n"
                + motivo + "\n\n"
                + "Inicia sesión, corrige lo indicado y vuelve a subir tu CV en PDF para que lo revise de nuevo:\n\n"
                + baseUrl + "/login\n\n"
                + "— SkillBridge AI";

        if (!enviar(correo, asunto, cuerpo)) {
            log.info("[EMAIL] Registro rechazado -> {} | motivo: {}", correo, motivo);
        }
    }

    public void enviarCodigoRecuperacion(String correo, String codigo) {
        String asunto = "Código de recuperación de contraseña — SkillBridge AI";
        String cuerpo = "Hola,\n\n"
                + "Recibimos una solicitud para restablecer tu contraseña. Tu código de recuperación es:\n\n"
                + codigo + "\n\n"
                + "Este código es válido por 15 minutos. Si no solicitaste esto, puedes ignorar este correo.\n\n"
                + "— SkillBridge AI";

        if (!enviar(correo, asunto, cuerpo)) {
            log.info("[EMAIL] Código de recuperación -> {} | código: {}", correo, codigo);
        }
    }

    // Devuelve true si intentó enviar por SMTP (con o sin éxito), false si cayó al modo log-only.
    private boolean enviar(String destinatario, String asunto, String cuerpo) {
        if (remitente == null || remitente.isBlank()) {
            return false;
        }
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom("SkillBridge AI <" + remitente + ">");
            mensaje.setTo(destinatario);
            mensaje.setSubject(asunto);
            mensaje.setText(cuerpo);
            mailSender.send(mensaje);
        } catch (Exception e) {
            log.warn("[EMAIL] No se pudo enviar el correo a {}: {}", destinatario, e.getMessage());
        }
        return true;
    }
}
