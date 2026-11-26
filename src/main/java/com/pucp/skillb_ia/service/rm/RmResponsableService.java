package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ConfiguracionSistemaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reparte los proyectos entre los Resource Managers y controla cuáles gestiona cada uno según su carga.
 * Lee de la configuración del sistema el tope de proyectos por RM ({@code MAX_PROYECTOS_POR_RM}, 5 por
 * defecto), los días para considerar urgente un inicio ({@code DIAS_URGENCIA_INICIO}, 7) y los días
 * máximos sin revisar ({@code DIAS_MAX_SIN_REVISAR}, 2). La usan el registro de proyectos del PM,
 * las acciones del RM y {@link RmRevisionScheduler}.
 */
@Service
public class RmResponsableService {

    private static final String ROL_RM = "RESOURCE_MANAGER";

    // Claves utilizadas para obtener los valores configurados en el sistema.
    private static final String CLAVE_MAX_PROYECTOS = "MAX_PROYECTOS_POR_RM";
    private static final String CLAVE_DIAS_URGENCIA = "DIAS_URGENCIA_INICIO";
    private static final String CLAVE_DIAS_SIN_REVISAR = "DIAS_MAX_SIN_REVISAR";

    // Valores que se utilizan cuando no existe una configuración válida.
    private static final int MAX_PROYECTOS_POR_DEFECTO = 5;
    private static final int DIAS_URGENCIA_POR_DEFECTO = 7;
    private static final int DIAS_SIN_REVISAR_POR_DEFECTO = 2;

    // Estados que se consideran para calcular los proyectos que tiene a cargo un RM.
    private static final List<EstadoProyecto> ESTADOS_CON_CARGA =
            List.of(EstadoProyecto.EN_REVISION, EstadoProyecto.ACTIVO, EstadoProyecto.EN_ESPERA);

    private final ProyectoRepository proyectoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionSistemaRepository configuracionRepository;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public RmResponsableService(ProyectoRepository proyectoRepository,
                                UsuarioRepository usuarioRepository,
                                ConfiguracionSistemaRepository configuracionRepository,
                                NotificacionService notificacionService,
                                AuditoriaService auditoriaService) {
        this.proyectoRepository = proyectoRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracionRepository = configuracionRepository;
        this.notificacionService = notificacionService;
        this.auditoriaService = auditoriaService;
    }


    // Busca un valor en la configuración del sistema y devuelve el valor por defecto
    // si no existe, está vacío o no contiene un número válido.
    private int leerEntero(String clave, int porDefecto) {
        return configuracionRepository.findByClave(clave)
                .map(c -> {
                    try {
                        int numero = Integer.parseInt(c.getValor().trim());
                        return numero > 0 ? numero : porDefecto;
                    } catch (NumberFormatException e) {
                        return porDefecto;
                    }
                })
                .orElse(porDefecto);
    }

    /**
     * Obtiene el máximo de proyectos en revisión, activos o en espera que puede tener un RM.
     *
     * @return valor configurado, o 5 si no existe o no es un entero positivo
     */
    public int maxProyectosPorRm() {
        return leerEntero(CLAVE_MAX_PROYECTOS, MAX_PROYECTOS_POR_DEFECTO);
    }

    /**
     * Obtiene los días de anticipación con que un inicio cercano vuelve urgente un proyecto.
     *
     * @return valor configurado, o 7 si no existe o no es un entero positivo
     */
    public int diasUrgenciaInicio() {
        return leerEntero(CLAVE_DIAS_URGENCIA, DIAS_URGENCIA_POR_DEFECTO);
    }

    // Obtiene cuántos días puede permanecer un proyecto sin revisión antes de escalarlo.
    private int diasMaxSinRevisar() {
        return leerEntero(CLAVE_DIAS_SIN_REVISAR, DIAS_SIN_REVISAR_POR_DEFECTO);
    }


    /**
     * Explica por qué un proyecto es urgente: prioridad alta, o fecha de inicio pasada o dentro de
     * {@link #diasUrgenciaInicio()} días.
     *
     * @param proyecto proyecto a evaluar
     * @return el motivo para mostrar, o {@code null} si no es urgente
     */
    public String motivoUrgencia(Proyecto proyecto) {
        if (proyecto.getPrioridad() == Prioridad.ALTA) {
            return "Prioridad alta";
        }
        LocalDate inicio = proyecto.getFechaInicio();
        if (inicio != null && !inicio.isAfter(LocalDate.now().plusDays(diasUrgenciaInicio()))) {
            if (inicio.isBefore(LocalDate.now())) {
                return "La fecha de inicio ya pasó";
            }
            return "Inicia pronto (" + inicio + ")";
        }
        return null;
    }

    /**
     * Indica si el proyecto es urgente según {@link #motivoUrgencia(Proyecto)}.
     *
     * @param proyecto proyecto a evaluar
     * @return {@code true} si es urgente
     */
    public boolean esUrgente(Proyecto proyecto) {
        return motivoUrgencia(proyecto) != null;
    }

    /**
     * Indica si el proyecto está en un estado que admite RM responsable (en revisión, activo o en espera).
     *
     * @param proyecto proyecto a evaluar
     * @return {@code true} si es gestionable
     */
    public boolean esGestionable(Proyecto proyecto) {
        return ESTADOS_CON_CARGA.contains(proyecto.getEstado());
    }



    /**
     * Cuenta los proyectos en revisión, activos o en espera que tiene a cargo el RM.
     *
     * @param rm Resource Manager
     * @return carga actual del RM
     */
    public long cargaDe(Usuario rm) {
        return proyectoRepository.countByRmRevisorAndEstadoIn(rm, ESTADOS_CON_CARGA);
    }

    // Cuenta únicamente los proyectos que tiene pendientes de revisión.
    private long enRevisionDe(Usuario rm) {
        List<EstadoProyecto> soloRevision = new ArrayList<>();
        soloRevision.add(EstadoProyecto.EN_REVISION);
        return proyectoRepository.countByRmRevisorAndEstadoIn(rm, soloRevision);
    }

    // Busca al RM con menos proyectos asignados. Si no se ignora el tope,
    // descarta a quienes ya alcanzaron el máximo permitido.
    // Si hay empate, se elige al que tenga menos proyectos pendientes de revisión.
    private Usuario elegirRm(boolean ignorarTope) {
        int tope = maxProyectosPorRm();
        List<Usuario> rms = usuarioRepository.findActivosByRolNombre(ROL_RM);

        Usuario elegido = null;
        long menorCarga = Long.MAX_VALUE;
        long menorRevision = Long.MAX_VALUE;

        for (Usuario rm : rms) {
            long carga = cargaDe(rm);
            if (!ignorarTope && carga >= tope) {
                continue;
            }
            long enRevision = enRevisionDe(rm);
            if (carga < menorCarga || (carga == menorCarga && enRevision < menorRevision)) {
                elegido = rm;
                menorCarga = carga;
                menorRevision = enRevision;
            }
        }
        return elegido;
    }

    // Guarda al RM como responsable del proyecto y avisa tanto al revisor
    // como al PM. También registra la asignación en el historial de auditoría.
    private void asignar(Proyecto proyecto, Usuario rm, String origen) {
        proyecto.setRmRevisor(rm);
        proyectoRepository.save(proyecto);

        String urgencia = motivoUrgencia(proyecto);
        String detalle = "Se te asignó el proyecto \"" + proyecto.getNombre() + "\" para su revisión."
                + (urgencia != null ? " Urgente: " + urgencia + "." : "");
        notificacionService.crear(rm, "PROYECTO_ASIGNADO_RM", CategoriaNotificacion.PROYECTO,
                urgencia != null ? "Proyecto urgente asignado" : "Nuevo proyecto asignado",
                detalle, "PROYECTO", proyecto.getId());

        notificacionService.crear(proyecto.getPm(), "PROYECTO_RM_ASIGNADO", CategoriaNotificacion.PROYECTO,
                "RM responsable asignado",
                nombreCompleto(rm) + " será el Resource Manager de tu proyecto \"" + proyecto.getNombre() + "\".",
                "PROYECTO", proyecto.getId());

        auditoriaService.registrar(null, "ASIGNAR_RM_PROYECTO", "PROYECTO", proyecto.getId(),
                origen + ": proyecto \"" + proyecto.getNombre() + "\" asignado a " + nombreCompleto(rm) + ".",
                null, nombreCompleto(rm), null);
    }

    /**
     * Asigna un RM a un proyecto recién registrado: el de menor carga que no llegó al tope (en empate,
     * el de menos proyectos en revisión). Si el proyecto es urgente puede ignorar el tope. Si no hay
     * RM disponible, queda sin responsable. Notifica al RM y al PM y lo registra en la auditoría.
     *
     * @param proyecto proyecto recién creado
     */
    @Transactional
    public void asignarAlCrear(Proyecto proyecto) {
        Usuario rm = elegirRm(false);
        if (rm == null && esUrgente(proyecto)) {
            rm = elegirRm(true);
        }
        if (rm != null) {
            asignar(proyecto, rm, "Asignación automática");
        }
    }



    /**
     * El RM toma como responsable un proyecto sin responsable. Si ya es suyo, no hace nada.
     * Notifica al PM y lo registra en la auditoría.
     *
     * @param proyectoId id del proyecto
     * @param rm RM autenticado
     * @throws IllegalArgumentException si el proyecto no existe
     * @throws IllegalStateException si el proyecto no está en revisión, activo o en espera; si ya lo tiene
     *         otro RM (también si otro lo tomó al mismo tiempo); o si el RM llegó a su tope y el proyecto
     *         no es urgente
     */
    @Transactional
    public void tomar(Long proyectoId, Usuario rm) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto solicitado."));
        tomarProyecto(proyecto, rm);
    }

    // Comprueba que el proyecto pueda ser tomado y que no tenga otro RM responsable.
    // También verifica el límite de proyectos, salvo que se trate de un proyecto urgente.
    private void tomarProyecto(Proyecto proyecto, Usuario rm) {
        if (!esGestionable(proyecto)) {
            throw new IllegalStateException("Solo se pueden tomar proyectos en revisión, activos o en espera.");
        }

        Usuario actual = proyecto.getRmRevisor();
        if (actual != null) {
            if (actual.getId().equals(rm.getId())) {
                return; // ya es suyo
            }
            throw new IllegalStateException("Este proyecto ya está a cargo de " + nombreCompleto(actual) + ".");
        }

        // Los proyectos urgentes pueden asignarse aunque el RM haya alcanzado su límite.
        if (!esUrgente(proyecto) && cargaDe(rm) >= maxProyectosPorRm()) {
            throw new IllegalStateException("Alcanzaste tu límite de " + maxProyectosPorRm()
                    + " proyectos. Finaliza o libera uno antes de tomar otro.");
        }

        // Intenta asignar el proyecto solo si todavía no tiene otro RM responsable.
        // Si otro RM lo tomó primero, la operación no realiza ningún cambio.
        int filas = proyectoRepository.reclamar(proyecto.getId(), rm);
        if (filas == 0) {
            throw new IllegalStateException("Otro Resource Manager tomó este proyecto hace un momento.");
        }
        proyecto.setRmRevisor(rm);

        notificacionService.crear(proyecto.getPm(), "PROYECTO_RM_ASIGNADO", CategoriaNotificacion.PROYECTO,
                "RM responsable asignado",
                nombreCompleto(rm) + " será el Resource Manager de tu proyecto \"" + proyecto.getNombre() + "\".",
                "PROYECTO", proyecto.getId());

        auditoriaService.registrar(rm, "TOMAR_PROYECTO", "PROYECTO", proyecto.getId(),
                "El RM tomó el proyecto \"" + proyecto.getNombre() + "\".", null, nombreCompleto(rm), null);
    }

    /**
     * El RM responsable libera el proyecto para que otro RM pueda tomarlo. Queda en la auditoría.
     *
     * @param proyectoId id del proyecto
     * @param rm RM autenticado
     * @throws IllegalArgumentException si el proyecto no existe
     * @throws IllegalStateException si el RM no es el responsable o el proyecto ya no está en revisión
     */
    @Transactional
    public void liberar(Long proyectoId, Usuario rm) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto solicitado."));

        Usuario actual = proyecto.getRmRevisor();
        if (actual == null || !actual.getId().equals(rm.getId())) {
            throw new IllegalStateException("Solo el RM responsable puede liberar este proyecto.");
        }
        if (proyecto.getEstado() != EstadoProyecto.EN_REVISION) {
            throw new IllegalStateException("Solo se puede liberar un proyecto que aún está en revisión.");
        }

        proyecto.setRmRevisor(null);
        proyectoRepository.save(proyecto);

        auditoriaService.registrar(rm, "LIBERAR_PROYECTO", "PROYECTO", proyecto.getId(),
                "El RM liberó el proyecto \"" + proyecto.getNombre() + "\".", nombreCompleto(rm), null, null);
    }

    /**
     * Comprueba que el RM sea el responsable antes de gestionar el proyecto. Si el proyecto no tiene
     * responsable y es gestionable, el RM lo toma con las mismas reglas que {@link #tomar}; si está
     * cerrado y sin responsable, se permite la acción sin asignarlo.
     *
     * @param proyecto proyecto a gestionar
     * @param rm RM autenticado
     * @throws IllegalStateException si el proyecto está a cargo de otro RM, o si al tomarlo no se cumplen
     *         las reglas de {@link #tomar}
     */
    @Transactional
    public void validarResponsable(Proyecto proyecto, Usuario rm) {
        Usuario responsable = proyecto.getRmRevisor();
        if (responsable == null) {
            if (esGestionable(proyecto)) {
                tomarProyecto(proyecto, rm);
            }
            return;
        }
        if (!responsable.getId().equals(rm.getId())) {
            throw new IllegalStateException("Este proyecto está a cargo de " + nombreCompleto(responsable)
                    + ". Solo el RM responsable puede gestionarlo.");
        }
    }


    /**
     * Escala los proyectos en revisión que son urgentes o llevan más de {@code DIAS_MAX_SIN_REVISAR} días
     * desde su creación: a los que no tienen RM les asigna el de menor carga (si nadie tiene cupo, ignora
     * el tope); al responsable de los demás le envía un recordatorio. Lo ejecuta {@link RmRevisionScheduler}.
     */
    @Transactional
    public void escalarProyectosEnRevision() {
        int dias = diasMaxSinRevisar();
        List<Proyecto> enRevision = proyectoRepository.findByEstado(EstadoProyecto.EN_REVISION);

        for (Proyecto proyecto : enRevision) {
            boolean urgente = esUrgente(proyecto);
            boolean vencido = proyecto.getFechaCreacion() != null
                    && !proyecto.getFechaCreacion().plusDays(dias).isAfter(LocalDateTime.now());

            if (!urgente && !vencido) {
                continue;
            }

            if (proyecto.getRmRevisor() == null) {

                // Si no tiene responsable, busca primero un RM que todavía tenga espacio.
                // Si ninguno está disponible, permite asignarlo ignorando el límite.
                Usuario rm = elegirRm(false);
                if (rm == null) {
                    rm = elegirRm(true);
                }
                if (rm != null) {
                    asignar(proyecto, rm, "Escalamiento por falta de revisión");
                }
            } else {
                // Si ya tiene responsable, le recuerda que debe revisar el proyecto.
                String motivo = urgente ? motivoUrgencia(proyecto) : "Lleva más de " + dias + " día(s) en revisión";
                notificacionService.crear(proyecto.getRmRevisor(), "PROYECTO_REVISION_PENDIENTE",
                        CategoriaNotificacion.PROYECTO, "Proyecto pendiente de revisión",
                        "El proyecto \"" + proyecto.getNombre() + "\" sigue esperando tu revisión. " + motivo + ".",
                        "PROYECTO", proyecto.getId());
            }
        }
    }

    // Devuelve el nombre y apellido del usuario para mostrarlo en notificaciones y registros.
    private String nombreCompleto(Usuario u) {
        return u.getNombre() + " " + u.getApellido();
    }
}