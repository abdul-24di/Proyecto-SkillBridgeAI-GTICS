package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ColHistorialProyectoView;
import com.pucp.skillb_ia.dto.ColPerfilRequeridoView;
import com.pucp.skillb_ia.dto.ColProyectoDetalleView;
import com.pucp.skillb_ia.dto.ColProyectoDisponibleView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.PenalizacionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Lógica de negocio de los proyectos y asignaciones del colaborador.
 * <p>
 * Cubre cuatro responsabilidades:
 * <ul>
 *   <li><b>Explorar proyectos</b>: lista los proyectos con sus cupos y perfiles requeridos.</li>
 *   <li><b>Solicitar incorporación</b>: crea una asignación pendiente (origen
 *       {@code SOLICITADA_COLABORADOR}) que luego aprueban el PM y el RM.</li>
 *   <li><b>Consultar</b>: mis solicitudes, mis asignaciones, historial y detalle de un proyecto.</li>
 *   <li><b>Entregar actividades</b>: el colaborador marca una actividad como lista para revisión
 *       con comentario y evidencia, y se aplican strikes si la entrega es tardía.</li>
 * </ul>
 * El colaborador nunca aprueba ni rechaza asignaciones: solo las solicita y las consulta.
 * Las reglas inválidas se informan lanzando {@link IllegalArgumentException} con un mensaje
 * apto para el usuario; el controller lo muestra como alerta.
 *
 * @see PenalizacionService
 */
@Service
public class ColaboradorProyectoService {

    /** Tipos MIME aceptados como evidencia de una actividad: PDF, JPG y PNG. */
    private static final Set<String> TIPOS_EVIDENCIA_PERMITIDOS =
            Set.of("application/pdf", "image/jpeg", "image/png");


    /** Extensiones aceptadas para la evidencia. Se validan además del tipo MIME. */
    private static final Set<String> EXTENSIONES_EVIDENCIA_PERMITIDAS =
            Set.of("pdf", "jpg", "jpeg", "png");

    /** Tamaño máximo de la evidencia: 10 MB, expresado en bytes. */
    public static final long TAMANO_MAXIMO_EVIDENCIA_BYTES = 10L * 1024 * 1024; // 10MB

    /** Máximo de caracteres del comentario al marcar una actividad como lista. */
    public static final int MAX_COMENTARIO_ENTREGA = 300;

    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;
    private final HabilidadRepository habilidadRepository;
    private final ActividadRepository actividadRepository;
    private final AuditoriaService auditoriaService;

    private final com.pucp.skillb_ia.service.PenalizacionService penalizacionService;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;
    private final ColaboradorExplorarService colaboradorExplorarService;
    private final NotificacionService notificacionService;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;

    /**
     * Inyecta por constructor las dependencias del service.
     *
     * @param proyectoRepository acceso a proyectos
     * @param asignacionRepository acceso a asignaciones
     * @param proyectoHabilidadRequeridaRepository perfiles (habilidades) que pide cada proyecto
     * @param habilidadRepository catálogo de habilidades
     * @param actividadRepository acceso a actividades
     * @param colaboradorExplorarService arma los perfiles públicos de los integrantes
     * @param auditoriaService registro en el log de auditoría
     * @param penalizacionService strikes y penalizaciones
     * @param notificacionService envío de notificaciones
     * @param archivoAlmacenamientoService guardado de archivos (evidencias)
     * @param colaboradorHabilidadRepository habilidades del colaborador
     */
    public ColaboradorProyectoService(ProyectoRepository proyectoRepository,
                                      AsignacionRepository asignacionRepository,
                                      ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository,
                                      HabilidadRepository habilidadRepository,
                                      ActividadRepository actividadRepository,
                                      ColaboradorExplorarService colaboradorExplorarService,
                                      AuditoriaService auditoriaService,
                                      PenalizacionService penalizacionService,
                                      NotificacionService notificacionService,
                                      ArchivoAlmacenamientoService archivoAlmacenamientoService,
                                      ColaboradorHabilidadRepository colaboradorHabilidadRepository) {
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.notificacionService = notificacionService;
        this.proyectoHabilidadRequeridaRepository = proyectoHabilidadRequeridaRepository;
        this.habilidadRepository = habilidadRepository;
        this.actividadRepository = actividadRepository;
        this.colaboradorExplorarService = colaboradorExplorarService;
        this.auditoriaService = auditoriaService;
        this.penalizacionService = penalizacionService;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
    }

    // ============================================================
    // CONSULTA DE PROYECTOS
    // ============================================================

    /**
     * Lista los proyectos que se muestran en "Explorar", ordenados del más reciente al más antiguo.
     * Se omiten los proyectos {@code RECHAZADO}; los demás estados sí aparecen (el botón de
     * postular se habilita solo si las postulaciones están abiertas).
     *
     * @param colaborador colaborador que explora
     * @return una tarjeta por proyecto, calculada para este colaborador
     */
    public List<ColProyectoDisponibleView> listarTodosLosProyectos(Usuario colaborador) {
        List<Proyecto> todosLosProyectos = proyectoRepository.findAllConPmOrderByFechaCreacionDesc();
        List<ColProyectoDisponibleView> vistas = new ArrayList<>();

        for (Proyecto proyecto : todosLosProyectos) {
            if (proyecto.getEstado() == EstadoProyecto.RECHAZADO) {
                continue;
            }
            vistas.add(construirVista(proyecto, colaborador));
        }
        return vistas;
    }



    /**
     * Calcula la tarjeta de un proyecto para un colaborador.
     * <ol>
     *   <li>Cuenta las asignaciones {@code ACTIVAS} del proyecto y detecta si el colaborador ya
     *       tiene una {@code PENDIENTE} o {@code ACTIVA}.</li>
     *   <li>Por cada perfil requerido: cupos = personas pedidas - asignaciones activas de esa
     *       habilidad. Las horas del perfil son las suyas o, si no tiene, las del proyecto.</li>
     *   <li>{@code cupos} del proyecto = colaboradores requeridos - activos.</li>
     *   <li>Las postulaciones están abiertas si el proyecto está {@code ACTIVO} y algún perfil
     *       tiene cupo.</li>
     * </ol>
     *
     * @param proyecto proyecto a evaluar
     * @param colaborador colaborador para quien se calcula
     * @return la vista lista para la pantalla
     */
    private ColProyectoDisponibleView construirVista(Proyecto proyecto, Usuario colaborador) {
        List<Asignacion> asignacionesDelProyecto = asignacionRepository.findByProyecto(proyecto);

        int activos = 0;
        boolean yaTieneSolicitud = false;

        for (Asignacion asignacion : asignacionesDelProyecto) {
            if (asignacion.getEstado() == EstadoAsignacion.ACTIVA) {
                activos++;
            }

            boolean esDeEsteColaborador = asignacion.getColaborador().getId().equals(colaborador.getId());
            boolean estaPendienteOActiva = asignacion.getEstado() == EstadoAsignacion.PENDIENTE
                    || asignacion.getEstado() == EstadoAsignacion.ACTIVA;

            if (esDeEsteColaborador && estaPendienteOActiva) {
                yaTieneSolicitud = true;
            }
        }


        List<ProyectoHabilidadRequerida> requeridas = proyectoHabilidadRequeridaRepository.findByProyecto(proyecto);
        List<ColPerfilRequeridoView> perfiles = new ArrayList<>();
        boolean algunPerfilConCupo = false;
        boolean algunPerfilConCupoYHorasSuficientes = false;

        for (ProyectoHabilidadRequerida requerida : requeridas) {
            int ocupadosDeEstePerfil = 0;
            for (Asignacion asignacion : asignacionesDelProyecto) {
                boolean esActiva = asignacion.getEstado() == EstadoAsignacion.ACTIVA;
                boolean esDeEstaHabilidad = asignacion.getHabilidadSolicitada() != null
                        && asignacion.getHabilidadSolicitada().getId().equals(requerida.getHabilidad().getId());
                if (esActiva && esDeEstaHabilidad) {
                    ocupadosDeEstePerfil++;
                }
            }

            int cuposPerfil = requerida.getCantidadPersonas() - ocupadosDeEstePerfil;
            if (cuposPerfil > 0) {
                algunPerfilConCupo = true;
            }


            BigDecimal horasDeEstePerfil = requerida.getHorasSemanales() != null ? requerida.getHorasSemanales() : proyecto.getHorasSemanalesRequeridas();

            if (cuposPerfil > 0 && tieneHorasSuficientes(colaborador, horasDeEstePerfil)) {
                algunPerfilConCupoYHorasSuficientes = true;
            }

            String nivel = requerida.getNivelRequerido() != null ? nombreNivel(requerida.getNivelRequerido()) : null;
            String etiqueta = requerida.getHabilidad().getNombre() + (nivel != null ? " (" + nivel + ")" : "");
            perfiles.add(new ColPerfilRequeridoView(requerida.getHabilidad().getId(), etiqueta, cuposPerfil, horasDeEstePerfil));
        }

        int cupos = proyecto.getColaboradoresRequeridos() - activos;

        // Postulaciones abiertas = proyecto activo Y al menos un perfil con cupo.
        boolean postulacionesAbiertas = proyecto.getEstado() == EstadoProyecto.ACTIVO && algunPerfilConCupo;

        return new ColProyectoDisponibleView(proyecto, activos, cupos, perfiles, yaTieneSolicitud,
                algunPerfilConCupoYHorasSuficientes, postulacionesAbiertas);
    }

    /**
     * Traduce un nivel de dominio a su texto visible.
     *
     * @param nivel nivel requerido
     * @return "Avanzado", "Intermedio" o "Básico" (este último para cualquier otro valor)
     */
    private String nombreNivel(NivelDominio nivel) {
        if (nivel == NivelDominio.AVANZADO) return "Avanzado";
        if (nivel == NivelDominio.INTERMEDIO) return "Intermedio";
        return "Básico";
    }

    /**
     * Indica si al colaborador le alcanzan las horas semanales disponibles para un perfil.
     *
     * @param colaborador colaborador evaluado
     * @param horasRequeridas horas semanales que exige el perfil
     * @return {@code true} si sus horas disponibles son mayores o iguales a las requeridas;
     *         {@code false} si alguno de los dos valores es {@code null}
     */
    private boolean tieneHorasSuficientes(Usuario colaborador, BigDecimal horasRequeridas) {
        BigDecimal disponibles = colaborador.getHorasDisponibles();
        if (disponibles == null || horasRequeridas == null) {
            return false;
        }
        return disponibles.compareTo(horasRequeridas) >= 0;
    }

    /**
     * Lista las solicitudes de incorporación que el propio colaborador envió, es decir, sus
     * asignaciones con origen {@code SOLICITADA_COLABORADOR}, de la más reciente a la más antigua.
     * Incluye cualquier estado (pendiente, activa, rechazada, finalizada).
     *
     * @param colaborador colaborador consultado
     * @return sus solicitudes enviadas
     */
    public List<Asignacion> listarMisSolicitudes(Usuario colaborador) {
        List<Asignacion> todasMisAsignaciones = asignacionRepository.findByColaborador(colaborador);
        List<Asignacion> misSolicitudes = new ArrayList<>();

        for (Asignacion asignacion : todasMisAsignaciones) {
            if (asignacion.getOrigen() == OrigenAsignacion.SOLICITADA_COLABORADOR) {
                misSolicitudes.add(asignacion);
            }
        }

        misSolicitudes.sort(Comparator.comparing(Asignacion::getFechaSolicitud).reversed());
        return misSolicitudes;
    }

    /**
     * Lista todas las asignaciones del colaborador, sin importar su origen ni estado, de la más
     * reciente a la más antigua por fecha de solicitud. Es una vista de solo lectura.
     *
     * @param colaborador colaborador consultado
     * @return sus asignaciones
     */
    public List<Asignacion> listarMisAsignaciones(Usuario colaborador) {
        List<Asignacion> encontradas = asignacionRepository.findByColaborador(colaborador);
        List<Asignacion> misAsignaciones = new ArrayList<>(encontradas);
        misAsignaciones.sort(Comparator.comparing(Asignacion::getFechaSolicitud).reversed());
        return misAsignaciones;
    }

    // ============================================================
    // SOLICITAR INCORPORACIÓN A UN PERFIL ESPECÍFICO DEL PROYECTO
    // ============================================================

    /**
     * Registra la solicitud del colaborador para incorporarse a un perfil de un proyecto.
     * <p>
     * Validaciones, en este orden (cada una lanza {@link IllegalArgumentException}):
     * <ol>
     *   <li>El proyecto existe y está {@code ACTIVO}.</li>
     *   <li>Se eligió un perfil (habilidad) y ese perfil pertenece al proyecto.</li>
     *   <li>El colaborador tiene esa habilidad en estado {@code VALIDADA}.</li>
     *   <li>Sus horas disponibles alcanzan las horas semanales del perfil.</li>
     *   <li>El perfil todavía tiene cupos.</li>
     *   <li>No tiene ya una asignación pendiente o activa en este proyecto.</li>
     * </ol>
     * Si todo está bien, guarda una asignación con origen {@code SOLICITADA_COLABORADOR}, deja
     * el registro en auditoría y notifica al PM del proyecto y a los RM. La asignación solo
     * pasa a activa cuando ambos la aprueben. Es transaccional: si algo falla, no se guarda nada.
     *
     * @param colaborador colaborador que solicita
     * @param proyectoId proyecto al que quiere unirse
     * @param habilidadId perfil (habilidad) al que postula
     * @param mensaje mensaje opcional; si está en blanco se guarda como {@code null}
     * @throws IllegalArgumentException si no se cumple alguna validación
     */
    @Transactional
    public void solicitarIncorporacion(Usuario colaborador, Long proyectoId, Long habilidadId,
                                       String mensaje) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("El proyecto seleccionado no existe."));


        if (proyecto.getEstado() != EstadoProyecto.ACTIVO) {
            throw new IllegalArgumentException("Este proyecto ya no está activo.");
        }
        if (habilidadId == null) {
            throw new IllegalArgumentException("Selecciona el perfil al que deseas postularte.");
        }

        Habilidad habilidad = habilidadRepository.findById(habilidadId)
                .orElseThrow(() -> new IllegalArgumentException("El perfil seleccionado no es válido."));

        ProyectoHabilidadRequerida requerida = proyectoHabilidadRequeridaRepository
                .findByProyectoAndHabilidad(proyecto, habilidad)
                .orElseThrow(() -> new IllegalArgumentException("Ese perfil no pertenece a este proyecto."));

        boolean tieneLaHabilidadValidada = false;
        for (ColaboradorHabilidad ch : colaboradorHabilidadRepository.findByColaboradorAndActivoTrue(colaborador)) {
            if (ch.getHabilidad().getId().equals(habilidadId) && ch.getEstadoValidacion() == EstadoValidacion.VALIDADA) {
                tieneLaHabilidadValidada = true;
                break;
            }
        }
        if (!tieneLaHabilidadValidada) {
            throw new IllegalArgumentException(
                    "No cuentas con la habilidad \"" + habilidad.getNombre() + "\" validada en tu perfil, así que no puedes postularte a ese perfil.");
        }

        BigDecimal horasDeEsePerfil = requerida.getHorasSemanales() != null
                ? requerida.getHorasSemanales() : proyecto.getHorasSemanalesRequeridas();
        if (!tieneHorasSuficientes(colaborador, horasDeEsePerfil)) {
            throw new IllegalArgumentException("No tienes suficientes horas disponibles para este perfil. Se requieren "
                    + horasDeEsePerfil + " horas/semana.");
        }
        List<Asignacion> asignacionesDelProyecto = asignacionRepository.findByProyecto(proyecto);

        int ocupadosDeEsePerfil = 0;
        boolean yaTieneSolicitud = false;

        for (Asignacion asignacion : asignacionesDelProyecto) {
            boolean esDeEsaHabilidad = asignacion.getHabilidadSolicitada() != null
                    && asignacion.getHabilidadSolicitada().getId().equals(habilidadId);
            if (asignacion.getEstado() == EstadoAsignacion.ACTIVA && esDeEsaHabilidad) {
                ocupadosDeEsePerfil++;
            }

            boolean esDeEsteColaborador = asignacion.getColaborador().getId().equals(colaborador.getId());
            boolean estaPendienteOActiva = asignacion.getEstado() == EstadoAsignacion.PENDIENTE
                    || asignacion.getEstado() == EstadoAsignacion.ACTIVA;
            if (esDeEsteColaborador && estaPendienteOActiva) {
                yaTieneSolicitud = true;
            }
        }

        if (ocupadosDeEsePerfil >= requerida.getCantidadPersonas()) {
            throw new IllegalArgumentException("Ese perfil ya no tiene cupos disponibles.");
        }
        if (yaTieneSolicitud) {
            throw new IllegalArgumentException("Ya tienes una solicitud pendiente o una asignación activa en este proyecto.");
        }

        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(colaborador);
        asignacion.setHorasSemanales(horasDeEsePerfil);
        asignacion.setOrigen(OrigenAsignacion.SOLICITADA_COLABORADOR);
        asignacion.setHabilidadSolicitada(habilidad);
        asignacion.setMensajeSolicitud(mensaje == null || mensaje.isBlank() ? null : mensaje.trim());

        Asignacion guardada = asignacionRepository.save(asignacion);

        auditoriaService.registrar(colaborador, "SOLICITAR_ASIGNACION", "ASIGNACION", guardada.getId(),
                "Solicitó incorporarse al proyecto \"" + proyecto.getNombre() + "\" para el perfil \""
                        + habilidad.getNombre() + "\".");

        String descripcionSolicitud = colaborador.getNombre() + " " + colaborador.getApellido() + " solicitó incorporarse al proyecto \"" + proyecto.getNombre() + "\".";

        notificacionService.crear(proyecto.getPm(), "SOLICITUD_INCORPORACION", CategoriaNotificacion.ASIGNACION,
                "Solicitud de incorporación pendiente",
                descripcionSolicitud, "ASIGNACION", guardada.getId());

        notificacionService.crearParaRmDelProyecto(proyecto, "SOLICITUD_INCORPORACION", CategoriaNotificacion.ASIGNACION,
                "Solicitud de incorporación pendiente",
                descripcionSolicitud, "ASIGNACION", guardada.getId());

    }

    // ============================================================
    // HISTORIAL DE PROYECTOS FINALIZADOS (Perfil Profesional)
    // ============================================================
    /**
     * Arma el historial de proyectos del perfil profesional: las asignaciones {@code FINALIZADA}
     * del colaborador, de la finalizada más reciente a la más antigua, cada una con las
     * habilidades que pedía su proyecto.
     *
     * @param colaborador colaborador consultado
     * @return historial de proyectos
     */
    public List<ColHistorialProyectoView> listarHistorialProyectos(Usuario colaborador) {
        List<Asignacion> finalizadas = asignacionRepository
                .findByColaboradorAndEstadoOrderByFechaFinalizacionDesc(colaborador, EstadoAsignacion.FINALIZADA);

        List<ColHistorialProyectoView> historial = new ArrayList<>();
        for (Asignacion asignacion : finalizadas) {
            Proyecto proyecto = asignacion.getProyecto();

            List<ProyectoHabilidadRequerida> requeridas = proyectoHabilidadRequeridaRepository.findByProyecto(proyecto);
            List<String> habilidades = new ArrayList<>();
            for (ProyectoHabilidadRequerida requerida : requeridas) {
                habilidades.add(requerida.getHabilidad().getNombre());
            }

            historial.add(new ColHistorialProyectoView(proyecto, asignacion, habilidades));
        }
        return historial;
    }


    /** Cantidad de actividades por página en el detalle del proyecto. */
    public static final int TAMANIO_PAGINA_ACTIVIDADES = 5;

    // ============================================================
    // DETALLE DE UN PROYECTO (Resumen/ Integrantes/ Actividades)
    // ============================================================
    /**
     * Arma todo lo que necesita la pantalla de detalle de un proyecto.
     * <p>
     * <b>Control de acceso.</b> Solo el dueño de la asignación puede entrar. Además la
     * asignación debe estar {@code ACTIVA}, o {@code FINALIZADA} con el proyecto ya
     * {@code FINALIZADO} o {@code CANCELADO}. Si lo desasignaron mientras el proyecto sigue en
     * marcha, pierde el acceso.
     * <p>
     * <b>Efecto secundario.</b> Antes de mostrar, revisa las actividades del colaborador que ya
     * vencieron sin entregarse y les aplica el strike correspondiente.
     * <p>
     * Los integrantes son las asignaciones {@code ACTIVAS} del proyecto y los perfiles públicos
     * incluyen también al PM. Las actividades se ordenan de la más reciente a la más antigua por
     * fecha de asignación y se paginan de a {@link #TAMANIO_PAGINA_ACTIVIDADES}.
     *
     * @param colaborador colaborador que consulta
     * @param asignacionId asignación del colaborador al proyecto
     * @param paginaTodas página de todas las actividades (texto; si no es válido, 1)
     * @param paginaMias página de las actividades propias (texto; si no es válido, 1)
     * @return la vista con todos los datos
     * @throws IllegalArgumentException si la asignación no existe, no es del colaborador o ya no tiene acceso
     */
    public ColProyectoDetalleView obtenerDetalleProyecto(Usuario colaborador, Long asignacionId,
                                                         String paginaTodas, String paginaMias) {
        Asignacion asignacion = asignacionRepository.findByIdConDetalle(asignacionId)
                .orElseThrow(() -> new IllegalArgumentException("El proyecto que buscas no existe."));

        if (!asignacion.getColaborador().getId().equals(colaborador.getId())) {
            throw new IllegalArgumentException("No tienes acceso a este proyecto.");
        }

        boolean esActiva = asignacion.getEstado() == EstadoAsignacion.ACTIVA;

        EstadoProyecto estadoProyecto = asignacion.getProyecto().getEstado();
        boolean proyectoTerminoParaTodos = estadoProyecto == EstadoProyecto.FINALIZADO
                || estadoProyecto == EstadoProyecto.CANCELADO;
        boolean esFinalizadaNormal = asignacion.getEstado() == EstadoAsignacion.FINALIZADA
                && proyectoTerminoParaTodos;

        if (!esActiva && !esFinalizadaNormal) {
            throw new IllegalArgumentException("Ya no tienes acceso a este proyecto: fuiste desasignado.");
        }

        Proyecto proyecto = asignacion.getProyecto();

        //Listamos a los integrantes que son colaboradores con asignación ACTIVA en ese proyecto
        List<Asignacion> integrantes = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);

        //Listamos las actividades del colaborador que está viendo el proyecto
        List<Actividad> todasLasActividades = actividadRepository.findByProyectoAndActivoTrue(proyecto);
        List<Actividad> misActividades = new ArrayList<>();
        for (Actividad actividad : todasLasActividades) {
            if (actividad.getColaborador().getId().equals(colaborador.getId())) {
                misActividades.add(actividad);
            }
        }

        //Revisamos si alguna actividad venció sin que la entregara.
        penalizacionService.revisarVencidasSinEntregar(misActividades);

        //Armamos el perfil público (habilidades, experiencia, educación) del PM y de cada integrante para mostrarlo.
        List<Usuario> personasDelProyecto = new ArrayList<>();
        personasDelProyecto.add(proyecto.getPm());
        for (Asignacion miembro : integrantes) {
            personasDelProyecto.add(miembro.getColaborador());
        }


        Map<Long, ColaboradorExplorarService.PerfilExplorar> perfilesIntegrantes =
                colaboradorExplorarService.obtenerPerfiles(personasDelProyecto);

        int misStrikes = penalizacionService.contarStrikes(colaborador, proyecto);

        List<Actividad> todasOrdenadas = new ArrayList<>(todasLasActividades);

        todasOrdenadas.sort(Comparator.comparing(Actividad::getFechaAsignacion).reversed());

        int totalPaginasTodas = totalPaginasActividades(todasOrdenadas.size());

        int paginaActualTodas = paginaActualActividades(paginaTodas, totalPaginasTodas);

        List<Actividad> actividadesDelProyectoPagina = recortarActividades(todasOrdenadas, paginaActualTodas);

        List<Actividad> misActividadesOrdenadas = new ArrayList<>(misActividades);

        misActividadesOrdenadas.sort(Comparator.comparing(Actividad::getFechaAsignacion).reversed());

        int totalPaginasMias = totalPaginasActividades(misActividadesOrdenadas.size());

        int paginaActualMias = paginaActualActividades(paginaMias, totalPaginasMias);

        List<Actividad> misActividadesPagina = recortarActividades(misActividadesOrdenadas, paginaActualMias);

        return new ColProyectoDetalleView(proyecto, asignacion, integrantes,
                actividadesDelProyectoPagina, misActividadesPagina, perfilesIntegrantes, misStrikes,
                paginaActualTodas, totalPaginasTodas, todasOrdenadas.size(),
                paginaActualMias, totalPaginasMias, misActividadesOrdenadas.size());
    }

    /**
     * Calcula cuántas páginas hay.
     *
     * @param totalRegistros cantidad total de actividades
     * @return número de páginas, mínimo 1 aunque no haya registros
     */
    private int totalPaginasActividades(int totalRegistros) {
        return Math.max(1, (int) Math.ceil(totalRegistros / (double) TAMANIO_PAGINA_ACTIVIDADES));
    }

    /**
     * Normaliza el número de página recibido.
     *
     * @param pagina texto con el número; puede ser {@code null} o inválido
     * @param totalPaginas total de páginas disponibles
     * @return la página pedida, 1 si viene vacía o no es número, y siempre entre 1 y {@code totalPaginas}
     */
    private int paginaActualActividades(String pagina, int totalPaginas) {
        int numero;
        try {
            numero = pagina == null ? 1 : Integer.parseInt(pagina.trim());
        } catch (NumberFormatException ex) {
            numero = 1;
        }
        return Math.min(Math.max(1, numero), totalPaginas);
    }

    /**
     * Devuelve solo las actividades de la página indicada.
     *
     * @param registros lista completa ya ordenada
     * @param paginaActual página a extraer (empieza en 1)
     * @return el tramo de la página, o una lista vacía si no hay registros en ella
     */
    private List<Actividad> recortarActividades(List<Actividad> registros, int paginaActual) {
        int desde = (paginaActual - 1) * TAMANIO_PAGINA_ACTIVIDADES;
        int hasta = Math.min(desde + TAMANIO_PAGINA_ACTIVIDADES, registros.size());
        return desde < hasta ? registros.subList(desde, hasta) : List.of();
    }

    // ============================================================
    // MARCAR ACTIVIDAD COMO "LISTO PARA REVISAR"
    // ============================================================

    /**
     * Valida el archivo de evidencia. A diferencia de otros métodos, no lanza excepción: devuelve
     * el mensaje de error para que el controller lo muestre junto al campo.
     * <p>
     * Reglas: debe existir (es obligatoria), ser PDF, JPG o PNG (se revisan tipo MIME y
     * extensión) y no superar 10 MB.
     *
     * @param evidencia archivo adjuntado
     * @return el mensaje de error, o {@code null} si la evidencia es válida
     */
    public String validarEvidencia(MultipartFile evidencia) {
        if (evidencia == null || evidencia.isEmpty()) {
            return "Debes adjuntar una evidencia para marcar la actividad como lista.";
        }
        if (evidencia.getContentType() == null || !TIPOS_EVIDENCIA_PERMITIDOS.contains(evidencia.getContentType())) {
            return "La evidencia debe estar en formato PDF, JPG o PNG.";
        }
        String nombre = evidencia.getOriginalFilename();
        if (nombre == null || !EXTENSIONES_EVIDENCIA_PERMITIDAS.contains(extensionDe(nombre))) {
            return "La evidencia debe ser un archivo .pdf, .jpg o .png.";
        }
        if (evidencia.getSize() > TAMANO_MAXIMO_EVIDENCIA_BYTES) {
            return "La evidencia supera el máximo de 10MB.";
        }
        return null;
    }


    /**
     * Marca una actividad como "lista para revisión". Es el paso del colaborador en el flujo de
     * entrega de dos pasos; después el PM la confirma o la devuelve.
     * <p>
     * Validaciones ({@link IllegalArgumentException}): la actividad existe y está activa, es del
     * colaborador, él sigue con asignación {@code ACTIVA} en el proyecto, no está ya
     * {@code EN_REVISION} ni {@code COMPLETADA}, la evidencia es válida y el comentario no está
     * vacío ni supera {@value #MAX_COMENTARIO_ENTREGA} caracteres.
     * <p>
     * Si todo es válido: guarda la evidencia, pasa la actividad a {@code EN_REVISION}, registra
     * la fecha y hora, registra auditoría y notifica al PM. <b>El sistema decide aquí si la entrega
     * fue a tiempo o tardía</b>, comparando la fecha de hoy con la fecha límite
     * ({@code TARDIA} si hoy es posterior). Si fue tardía, además aplica un strike al
     * colaborador y verifica si alcanzó el límite de strikes en el proyecto.
     *
     * @param colaborador colaborador que entrega
     * @param actividadId actividad que se marca
     * @param evidencia archivo de evidencia (obligatorio)
     * @param comentario comentario del colaborador (obligatorio)
     * @throws IllegalArgumentException si no se cumple alguna validación
     * @see PenalizacionService#aplicarStrikePorTardanza
     */
    @Transactional
    public void marcarActividadListaParaRevision(Usuario colaborador, Long actividadId,
                                                 MultipartFile evidencia, String comentario) {
        Actividad actividad = actividadRepository.findById(actividadId)
                .orElseThrow(() -> new IllegalArgumentException("La actividad no existe."));

        if (!actividad.isActivo()) {
            throw new IllegalArgumentException("Esta actividad ya no existe.");
        }
        if (!actividad.getColaborador().getId().equals(colaborador.getId())) {
            throw new IllegalArgumentException("Esta actividad no te pertenece.");
        }
        boolean sigueActivoEnElProyecto = asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                actividad.getProyecto(), colaborador, EstadoAsignacion.ACTIVA);
        if (!sigueActivoEnElProyecto) {
            throw new IllegalArgumentException("Ya no tienes una asignación activa en este proyecto.");
        }
        if (actividad.getEstado() == EstadoActividad.EN_REVISION) {
            throw new IllegalArgumentException("Esta actividad ya está en revisión.");
        }
        if (actividad.getEstado() == EstadoActividad.COMPLETADA) {
            throw new IllegalArgumentException("Esta actividad ya fue completada.");
        }
        String errorEvidencia = validarEvidencia(evidencia);
        if (errorEvidencia != null) {
            throw new IllegalArgumentException(errorEvidencia);
        }

        if (comentario == null || comentario.isBlank()) {
            throw new IllegalArgumentException("Debes escribir un comentario para marcar la actividad como lista.");
        }
        if (comentario.trim().length() > MAX_COMENTARIO_ENTREGA) {
            throw new IllegalArgumentException("El comentario no puede superar los " + MAX_COMENTARIO_ENTREGA + " caracteres.");
        }

        String evidenciaUrl = guardarEvidencia(evidencia, actividad.getId());

        boolean entregadaTarde = actividad.getFechaLimite() != null
                && java.time.LocalDate.now().isAfter(actividad.getFechaLimite());
        actividad.setEvidenciaUrl(evidenciaUrl);
        actividad.setComentarioColaborador(comentario.trim());
        actividad.setEstado(EstadoActividad.EN_REVISION);
        actividad.setEstadoEntrega(entregadaTarde ? EstadoEntrega.TARDIA : EstadoEntrega.A_TIEMPO);
        actividad.setFechaMarcadoRevision(LocalDateTime.now());
        actividadRepository.save(actividad);

        auditoriaService.registrar(colaborador, "MARCAR_ACTIVIDAD_LISTA", "ACTIVIDAD", actividad.getId(),
                "Marcó la actividad \"" + actividad.getTitulo() + "\" como lista para revisión.");

        notificacionService.crear(actividad.getProyecto().getPm(), "ACTIVIDAD_LISTA", com.pucp.skillb_ia.model.enums.CategoriaNotificacion.ACTIVIDAD,
                "Actividad lista para revisar",
                colaborador.getNombre() + " " + colaborador.getApellido() + " marcó \"" + actividad.getTitulo() + "\" como lista.",
                "ACTIVIDAD", actividad.getId());

        // Si la entregó después de la fecha límite, es un strike. No importa sí la entregó, esta fue entregada tarde.
        if (entregadaTarde) {
            penalizacionService.aplicarStrikePorTardanza(actividad);
            penalizacionService.verificarYNotificarPorStrikes(colaborador, actividad.getProyecto());
        }
    }

    /**
     * Guarda el archivo de evidencia con un nombre único
     * ({@code actividad-<id>-<uuid>.<ext>}) en la carpeta {@code evidencias-actividades}.
     *
     * @param archivo evidencia ya validada
     * @param actividadId id de la actividad, usado en el nombre del archivo
     * @return la ruta o URL donde quedó guardado
     */
    private String guardarEvidencia(MultipartFile archivo, Long actividadId) {
        String extension;
        if ("application/pdf".equals(archivo.getContentType())) {
            extension = ".pdf";
        } else if ("image/png".equals(archivo.getContentType())) {
            extension = ".png";
        } else {
            extension = ".jpg";
        }


        String nombreArchivo = "actividad-" + actividadId + "-" + UUID.randomUUID() + extension;
        return archivoAlmacenamientoService.guardar(archivo, "evidencias-actividades", nombreArchivo);
    }


    /**
     * Obtiene la extensión de un nombre de archivo, en minúsculas.
     *
     * @param nombreArchivo nombre del archivo
     * @return la extensión sin punto, o cadena vacía si no tiene o el punto está al final
     */
    private String extensionDe(String nombreArchivo) {
        int punto = nombreArchivo.lastIndexOf('.');
        if (punto == -1 || punto == nombreArchivo.length() - 1) {
            return "";
        }
        return nombreArchivo.substring(punto + 1).toLowerCase();
    }

}