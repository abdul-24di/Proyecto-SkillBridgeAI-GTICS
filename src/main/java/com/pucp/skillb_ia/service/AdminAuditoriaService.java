package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Épica 5 (Log de auditoría) — pantalla de solo lectura sobre log_auditoria,
// que ya alimentan AuditoriaService.registrar(...) todos los demás módulos.
@Service
public class AdminAuditoriaService {

    private static final DateTimeFormatter FECHA_FORMATO = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private static final Map<String, String> ROL_A_ETIQUETA = Map.of(
            "ADMINISTRADOR", "Administrador",
            "RESOURCE_MANAGER", "Resource Manager",
            "PROJECT_MANAGER", "Project Manager",
            "COLABORADOR", "Colaborador"
    );
    private static final Map<String, String> ROL_A_BADGE = Map.of(
            "ADMINISTRADOR", "bg-azure-lt text-azure",
            "RESOURCE_MANAGER", "bg-green-lt text-green",
            "PROJECT_MANAGER", "bg-blue-lt text-blue",
            "COLABORADOR", "bg-yellow-lt text-yellow"
    );

    // El badge de color se agrupa por entidad (módulo) — así acciones del mismo
    // módulo (crear/editar/desactivar un usuario, por ejemplo) se ven del mismo color.
    private static final Map<String, String> ENTIDAD_A_ETIQUETA = new LinkedHashMap<>();
    private static final String[] PALETA_BADGES = {
            "bg-blue-lt text-blue", "bg-purple-lt text-purple", "bg-orange-lt text-orange",
            "bg-red-lt text-red", "bg-teal-lt text-teal", "bg-pink-lt text-pink",
            "bg-lime-lt text-lime", "bg-cyan-lt text-cyan"
    };

    // Etiqueta legible por cada código de `accion` concreto (más específico que
    // agrupar solo por entidad). Lo que no está mapeado se formatea genérico
    // (guiones bajos -> espacios) para que una acción nueva no rompa la pantalla.
    private static final Map<String, String> ACCION_A_ETIQUETA = new LinkedHashMap<>();

    static {
        ENTIDAD_A_ETIQUETA.put("USUARIO", "Gestión de usuarios");
        ENTIDAD_A_ETIQUETA.put("HABILIDAD", "Catálogo de habilidades");
        ENTIDAD_A_ETIQUETA.put("COLABORADOR_HABILIDAD", "Habilidades de colaborador");
        ENTIDAD_A_ETIQUETA.put("CONFIGURACION", "Configuración");
        ENTIDAD_A_ETIQUETA.put("ASIGNACION", "Asignaciones");
        ENTIDAD_A_ETIQUETA.put("CERTIFICADO", "Certificados");
        ENTIDAD_A_ETIQUETA.put("PROYECTO", "Proyectos");
        ENTIDAD_A_ETIQUETA.put("ACTIVIDAD", "Actividades");
        ENTIDAD_A_ETIQUETA.put("DOCUMENTO", "Documentos");
        ENTIDAD_A_ETIQUETA.put("EDUCACION", "Educación");
        ENTIDAD_A_ETIQUETA.put("COLABORADOR_CURSO", "Cursos");
        ENTIDAD_A_ETIQUETA.put("SOLICITUD_PERSONAL", "Solicitudes de personal");
        ENTIDAD_A_ETIQUETA.put("PUBLICACION_FORO", "Foro");
        ENTIDAD_A_ETIQUETA.put("RESPUESTA_FORO", "Foro");
        ENTIDAD_A_ETIQUETA.put("FORO", "Foro");

        // Auth / usuarios
        ACCION_A_ETIQUETA.put("LOGIN", "Inicio de sesión");
        ACCION_A_ETIQUETA.put("CREAR_USUARIO", "Creó usuario");
        ACCION_A_ETIQUETA.put("REENVIAR_ACTIVACION", "Reenvió activación");
        ACCION_A_ETIQUETA.put("ENVIO_CORREO_ACTIVACION", "Envió correo de activación");
        ACCION_A_ETIQUETA.put("ACTIVACION_CUENTA", "Activó cuenta");
        ACCION_A_ETIQUETA.put("RECUPERACION_CONTRASENA", "Recuperó contraseña");
        ACCION_A_ETIQUETA.put("CAMBIAR_ROL", "Cambió rol");
        ACCION_A_ETIQUETA.put("DESACTIVAR_USUARIO", "Desactivó usuario");
        ACCION_A_ETIQUETA.put("REACTIVAR_USUARIO", "Reactivó usuario");
        ACCION_A_ETIQUETA.put("CARGA_MASIVA_USUARIOS", "Carga masiva de usuarios");
        ACCION_A_ETIQUETA.put("ACTUALIZAR_PERFIL", "Actualizó perfil");
        ACCION_A_ETIQUETA.put("CAMBIO_PASSWORD", "Cambió contraseña");
        ACCION_A_ETIQUETA.put("CAMBIAR_PASSWORD", "Cambió contraseña");

        // Habilidades
        ACCION_A_ETIQUETA.put("CREAR_HABILIDAD", "Creó habilidad");
        ACCION_A_ETIQUETA.put("EDITAR_HABILIDAD", "Editó habilidad");
        ACCION_A_ETIQUETA.put("DESACTIVAR_HABILIDAD", "Desactivó habilidad");
        ACCION_A_ETIQUETA.put("REACTIVAR_HABILIDAD", "Reactivó habilidad");
        ACCION_A_ETIQUETA.put("AGREGAR_HABILIDAD", "Agregó habilidad a su perfil");
        ACCION_A_ETIQUETA.put("ELIMINAR_HABILIDAD", "Eliminó habilidad de su perfil");

        // Configuración
        ACCION_A_ETIQUETA.put("EDITAR_CONFIGURACION", "Editó parámetro");

        // Proyectos / asignaciones
        ACCION_A_ETIQUETA.put("CREAR", "Creó");
        ACCION_A_ETIQUETA.put("CANCELAR", "Canceló");
        ACCION_A_ETIQUETA.put("PROPONER", "Propuso asignación");
        ACCION_A_ETIQUETA.put("APROBAR", "Aprobó");
        ACCION_A_ETIQUETA.put("RECHAZAR", "Rechazó");
        ACCION_A_ETIQUETA.put("FINALIZAR", "Finalizó");
        ACCION_A_ETIQUETA.put("CONFIRMAR", "Confirmó");
        ACCION_A_ETIQUETA.put("DEVOLVER", "Devolvió");
        ACCION_A_ETIQUETA.put("PUBLICAR", "Publicó");
        ACCION_A_ETIQUETA.put("APROBAR_PROYECTO", "Aprobó proyecto");
        ACCION_A_ETIQUETA.put("RECHAZAR_PROYECTO", "Rechazó proyecto");
        ACCION_A_ETIQUETA.put("ASIGNAR_PRESUPUESTO_PROYECTO", "Asignó presupuesto");
        ACCION_A_ETIQUETA.put("ACTUALIZAR_PRESUPUESTO_PROYECTO", "Actualizó presupuesto");
        ACCION_A_ETIQUETA.put("PROPUESTA_ASIGNACION", "Propuso asignación (RM)");
        ACCION_A_ETIQUETA.put("APROBACION_ASIGNACION", "Aprobó asignación (RM)");
        ACCION_A_ETIQUETA.put("RECHAZO_ASIGNACION", "Rechazó asignación (RM)");
        ACCION_A_ETIQUETA.put("FINALIZACION_ASIGNACION", "Finalizó asignación (RM)");
        ACCION_A_ETIQUETA.put("SOLICITAR_ASIGNACION", "Solicitó asignación");
        ACCION_A_ETIQUETA.put("MARCAR_ACTIVIDAD_LISTA", "Marcó actividad como lista");

        // Certificados / educación / cursos / documentos
        ACCION_A_ETIQUETA.put("APROBACION_CERTIFICADO", "Aprobó certificado");
        ACCION_A_ETIQUETA.put("RECHAZO_CERTIFICADO", "Rechazó certificado");
        ACCION_A_ETIQUETA.put("ACTUALIZACION_NIVEL_EXPERIENCIA", "Actualizó nivel de experiencia");
        ACCION_A_ETIQUETA.put("SUBIR_CERTIFICADO", "Subió certificado");
        ACCION_A_ETIQUETA.put("AGREGAR_EDUCACION", "Agregó educación");
        ACCION_A_ETIQUETA.put("ELIMINAR_EDUCACION", "Eliminó educación");
        ACCION_A_ETIQUETA.put("SUBIR_DOCUMENTO", "Subió documento");
        ACCION_A_ETIQUETA.put("ELIMINAR_DOCUMENTO", "Eliminó documento");
        ACCION_A_ETIQUETA.put("SOLICITAR_CURSO", "Solicitó curso");

        // Solicitudes de personal
        ACCION_A_ETIQUETA.put("CREACION_SOLICITUD_PERSONAL", "Creó solicitud de personal");
        ACCION_A_ETIQUETA.put("INICIO_ATENCION_SOLICITUD", "Inició atención de solicitud");
        ACCION_A_ETIQUETA.put("CIERRE_SOLICITUD_PERSONAL", "Cerró solicitud de personal");

        // Foro
        ACCION_A_ETIQUETA.put("CREAR_PUBLICACION_FORO", "Creó publicación en foro");
        ACCION_A_ETIQUETA.put("EDITAR_PUBLICACION_FORO", "Editó publicación en foro");
        ACCION_A_ETIQUETA.put("ELIMINAR_PUBLICACION_FORO", "Eliminó publicación en foro");
        ACCION_A_ETIQUETA.put("CREAR_RESPUESTA_FORO", "Respondió en foro");
        ACCION_A_ETIQUETA.put("EDITAR_RESPUESTA_FORO", "Editó respuesta en foro");
        ACCION_A_ETIQUETA.put("ELIMINAR_RESPUESTA_FORO", "Eliminó respuesta en foro");
    }

    public record LogFila(String fecha, LocalDate fechaSolo, String usuarioNombre, String usuarioRolNombre,
                           String rolEtiqueta, String rolBadgeClase, String entidad, String accion,
                           String accionEtiqueta, String accionBadgeClase, String detalle) {
    }

    public record AccionOpcion(String valor, String etiqueta) {
    }

    public record FiltrosAuditoria(String texto, String rol, String accion, LocalDate desde, LocalDate hasta) {
    }

    private final LogAuditoriaRepository logAuditoriaRepository;

    public AdminAuditoriaService(LogAuditoriaRepository logAuditoriaRepository) {
        this.logAuditoriaRepository = logAuditoriaRepository;
    }

    @Transactional(readOnly = true)
    public List<LogFila> listar(FiltrosAuditoria filtros) {
        String texto = filtros.texto() != null ? filtros.texto().trim().toLowerCase() : "";

        return logAuditoriaRepository.findAllConUsuario().stream()
                .map(this::aFila)
                .filter(f -> texto.isEmpty() || f.usuarioNombre().toLowerCase().contains(texto))
                .filter(f -> filtros.rol() == null || filtros.rol().isBlank() || filtros.rol().equals("all")
                        || filtros.rol().equals(f.rolEtiqueta()))
                .filter(f -> filtros.accion() == null || filtros.accion().isBlank() || filtros.accion().equals("all")
                        || filtros.accion().equals(f.accion()))
                .filter(f -> filtros.desde() == null || !f.fechaSolo().isBefore(filtros.desde()))
                .filter(f -> filtros.hasta() == null || !f.fechaSolo().isAfter(filtros.hasta()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccionOpcion> tiposDeAccionDisponibles() {
        return logAuditoriaRepository.findAllConUsuario().stream()
                .map(LogAuditoria::getAccion)
                .distinct()
                .map(a -> new AccionOpcion(a, etiquetaAccion(a)))
                .sorted(Comparator.comparing(AccionOpcion::etiqueta))
                .toList();
    }

    private LogFila aFila(LogAuditoria l) {
        Usuario u = l.getUsuario();
        String usuarioNombre;
        String rolNombre;
        String rolEtiqueta;
        String rolBadge;

        if (u == null) {
            usuarioNombre = "Sistema";
            rolNombre = null;
            rolEtiqueta = "Automático";
            rolBadge = "bg-secondary-lt text-secondary";
        } else {
            String nombreCompleto = ((u.getNombre() != null ? u.getNombre() : "") + " " + (u.getApellido() != null ? u.getApellido() : "")).trim();
            usuarioNombre = nombreCompleto.isEmpty() ? u.getCorreo() : nombreCompleto;
            rolNombre = u.getRol().getNombre();
            rolEtiqueta = ROL_A_ETIQUETA.getOrDefault(rolNombre, rolNombre);
            rolBadge = ROL_A_BADGE.getOrDefault(rolNombre, "bg-secondary-lt text-secondary");
        }

        String entidad = l.getEntidad();
        String accion = l.getAccion();
        String accionEtiqueta = etiquetaAccion(accion);
        String accionBadge = badgeParaEntidad(entidad);

        return new LogFila(l.getFechaHora().format(FECHA_FORMATO), l.getFechaHora().toLocalDate(),
                usuarioNombre, rolNombre, rolEtiqueta, rolBadge, entidad, accion, accionEtiqueta, accionBadge,
                l.getDetalle());
    }

    private static String etiquetaAccion(String accion) {
        if (accion == null) return "—";
        if (ACCION_A_ETIQUETA.containsKey(accion)) return ACCION_A_ETIQUETA.get(accion);
        String texto = accion.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private String badgeParaEntidad(String entidad) {
        int indice = Math.floorMod(entidad.hashCode(), PALETA_BADGES.length);
        return PALETA_BADGES[indice];
    }
}
