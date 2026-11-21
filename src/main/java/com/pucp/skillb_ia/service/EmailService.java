package com.pucp.skillb_ia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Envía correos reales por SMTP cuando MAIL_USERNAME/MAIL_PASSWORD están
// configurados (ver application.properties). Si no lo están (por ejemplo, un
// compañero sin credenciales de Gmail configuradas localmente), cae de
// vuelta a solo loguear a consola para no romper el flujo de activación.
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String remitente;
    private final String baseUrl;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine,
                         @Value("${app.mail.from:}") String remitente,
                         @Value("${app.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.remitente = remitente;
        this.baseUrl = baseUrl;
        // En el servidor esto explica en el log por qué no llegan los correos.
        if (remitente == null || remitente.isBlank()) {
            log.warn("[EMAIL] Correo desactivado: falta la variable de entorno MAIL_USERNAME (y MAIL_PASSWORD). "
                    + "Los enlaces solo se mostrarán en este log.");
        }
    }

    // Base de los enlaces de los correos. Si APP_BASE_URL no se configuró (queda localhost) pero la petición
    // llega por otra dirección, como la IP pública del servidor, se usa esa: si no, el enlace apuntaría a
    // la PC de quien lo abre.
    private String urlBase() {
        if (baseUrl.contains("localhost")
                && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            HttpServletRequest peticion = atributos.getRequest();
            String host = peticion.getServerName();
            if (!"localhost".equals(host) && !"127.0.0.1".equals(host)) {
                return ServletUriComponentsBuilder.fromContextPath(peticion).build().toUriString();
            }
        }
        return baseUrl;
    }

    // Devuelve true solo si el correo salió por SMTP.
    // rol: nombre del rol en la BD (COLABORADOR, PROJECT_MANAGER...). Se muestra en el correo para que la persona
    // sepa con qué rol se creó su cuenta.
    public boolean enviarActivacion(String correo, String enlaceActivacion, String rol, int horasVigencia) {
        String enlaceCompleto = urlBase() + enlaceActivacion;
        String rolLegible = nombreRol(rol);
        String asunto = "Activa tu cuenta en SkillBridge AI";
        String cuerpo = "Hola,\n\n"
                + "Se creó una cuenta de " + rolLegible + " para ti en SkillBridge AI. Para activarla, completar tus datos "
                + "(y subir tu CV si eres colaborador) y elegir tu contraseña, "
                + "entra al siguiente enlace (válido por " + horasVigencia + " horas):\n\n"
                + enlaceCompleto + "\n\n"
                + "Si no esperabas este correo, puedes ignorarlo.\n\n"
                + "— SkillBridge AI";

        Context datos = new Context();
        datos.setVariable("enlace", enlaceCompleto);
        datos.setVariable("rol", rolLegible);
        datos.setVariable("esColaborador", "COLABORADOR".equals(rol));
        datos.setVariable("horasVigencia", horasVigencia);

        if (!enviar(correo, asunto, cuerpo, "email/activacion", datos)) {
            log.info("[EMAIL] Activación de cuenta -> {} | enlace: {}", correo, enlaceCompleto);
            return false;
        }
        return true;
    }

    // Se avisa por correo porque, mientras su registro está en revisión, el colaborador
    // no tiene el sistema abierto para ver la notificación.
    public void enviarRechazoRegistro(String correo, String motivo) {
        String asunto = "Tu registro en SkillBridge AI necesita correcciones";
        String cuerpo = "Hola,\n\n"
                + "El Administrador revisó tu registro y lo devolvió con la siguiente observación:\n\n"
                + motivo + "\n\n"
                + "Inicia sesión, corrige lo indicado y vuelve a subir tu CV en PDF para que lo revise de nuevo:\n\n"
                + urlBase() + "/login\n\n"
                + "— SkillBridge AI";

        if (!enviar(correo, asunto, cuerpo)) {
            log.info("[EMAIL] Registro rechazado -> {} | motivo: {}", correo, motivo);
        }
    }

    // TASK-056: misma información que la notificación CURSO_APROBADO (fechas y horas ya formateadas).
    public void enviarInscripcionAprobada(String correo, String curso, String fechaInicio,
                                          String fechaFin, String horas) {
        String asunto = "Tu inscripción al curso “" + curso + "” fue aprobada — SkillBridge AI";
        String cuerpo = "Hola,\n\n"
                + "El Resource Manager aprobó tu solicitud para el curso “" + curso + "”.\n\n"
                + "Fecha de inicio: " + fechaInicio + "\n"
                + "Fecha de fin: " + fechaFin + "\n"
                + "Duración: " + horas + "\n\n"
                + "Al terminar, sube la evidencia de finalización desde \"Mis cursos\" en tu perfil:\n\n"
                + urlBase() + "/colaborador/perfil#mis-cursos\n\n"
                + "— SkillBridge AI";

        if (!enviar(correo, asunto, cuerpo)) {
            log.info("[EMAIL] Inscripción aprobada -> {} | curso: {} | inicio: {} | fin: {} | duración: {}",
                    correo, curso, fechaInicio, fechaFin, horas);
        }
    }

    public void enviarCodigoRecuperacion(String correo, String codigo, int minutosVigencia) {
        String asunto = "Código de recuperación de contraseña — SkillBridge AI";
        String cuerpo = "Hola,\n\n"
                + "Recibimos una solicitud para restablecer tu contraseña. Tu código de recuperación es:\n\n"
                + codigo + "\n\n"
                + "Este código es válido por " + minutosVigencia + " minutos. "
                + "Si no solicitaste esto, puedes ignorar este correo.\n\n"
                + "— SkillBridge AI";

        Context datos = new Context();
        datos.setVariable("codigo", codigo);
        datos.setVariable("minutosVigencia", minutosVigencia);

        if (!enviar(correo, asunto, cuerpo, "email/recuperacion", datos)) {
            log.info("[EMAIL] Código de recuperación -> {} | código: {}", correo, codigo);
        }
    }

    // Arma el HTML del correo con una plantilla de templates/email (el mismo Thymeleaf de las vistas).
    // Público para poder revisar el resultado en las pruebas sin enviar nada.
    public String armarHtml(String plantilla, Context datos) {
        return templateEngine.process(plantilla, datos);
    }

    static String nombreRol(String rol) {
        if (rol == null) {
            return "usuario";
        }
        return switch (rol) {
            case "ADMINISTRADOR" -> "Administrador";
            case "PROJECT_MANAGER" -> "Project Manager";
            case "RESOURCE_MANAGER" -> "Resource Manager";
            case "COLABORADOR" -> "Colaborador";
            default -> rol;
        };
    }

    // Devuelve true si intentó enviar por SMTP (con o sin éxito), false si cayó al modo log-only.
    // true solo si el correo salió. Antes devolvía true aunque el envío fallara, y el error quedaba oculto
    // (en el servidor el Admin veía "se envió su correo" aunque nunca llegara).
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
            return true;
        } catch (Exception e) {
            log.error("[EMAIL] No se pudo enviar el correo a {}. Causa: {}", destinatario, e.getMessage(), e);
            return false;
        }
    }

    // Igual que el anterior, pero con diseño: va el HTML de la plantilla y, como respaldo, el texto plano
    // para los lectores de correo que no muestran HTML.
    private boolean enviar(String destinatario, String asunto, String textoPlano, String plantilla, Context datos) {
        if (remitente == null || remitente.isBlank()) {
            return false;
        }
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper ayudante = new MimeMessageHelper(mensaje, true, "UTF-8");
            ayudante.setFrom("SkillBridge AI <" + remitente + ">");
            ayudante.setTo(destinatario);
            ayudante.setSubject(asunto);
            ayudante.setText(textoPlano, armarHtml(plantilla, datos));
            mailSender.send(mensaje);
            return true;
        } catch (Exception e) {
            log.error("[EMAIL] No se pudo enviar el correo a {}. Causa: {}", destinatario, e.getMessage(), e);
            return false;
        }
    }
}
