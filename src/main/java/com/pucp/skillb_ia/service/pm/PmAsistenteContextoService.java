package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmAsistenteRespuesta.CandidatoTarjeta;
import com.pucp.skillb_ia.dto.PmAsistenteRespuesta.ConsultaRapida;
import com.pucp.skillb_ia.dto.PmAsistenteRespuesta.Fuente;
import com.pucp.skillb_ia.dto.PmAsistenteRespuesta.KpisAsistente;
import com.pucp.skillb_ia.dto.PmAsistenteRespuesta.ProyectoTarjeta;
import com.pucp.skillb_ia.dto.PmCupoHabilidadView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoEntrega;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.AsistenteSeguridadService;
import com.pucp.skillb_ia.service.PenalizacionService;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.HabilidadFaltante;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.RecomendacionCandidato;
import org.jsoup.Jsoup;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

@Service
public class PmAsistenteContextoService {

    private static final int MAX_PUBLICACIONES = 4;

    private static final Set<String> IGNORAR = new HashSet<>(Arrays.asList(
            "como", "para", "que", "con", "los", "las", "una", "uno", "por", "del", "mis", "mas", "muy",
            "puedo", "puede", "quiero", "necesito", "tengo", "sobre", "este", "esta", "esto", "cual",
            "cuales", "hacer", "hay", "donde", "cuando", "desde", "hasta", "pero", "tambien", "proyecto",
            "proyectos", "equipo", "equipos", "colaborador", "colaboradores", "ayuda", "favor", "dime"));

    public record ContextoPm(
            String textoPrompt,
            String resumenAnaliticoLocal,
            Proyecto proyectoEnfocado,
            KpisAsistente kpis,
            Map<Long, ProyectoTarjeta> proyectos,
            Map<String, CandidatoTarjeta> candidatos,
            Map<Long, Fuente> publicaciones) {}

    private final UsuarioRepository usuarioRepository;
    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final ActividadRepository actividadRepository;
    private final PublicacionForoRepository publicacionForoRepository;
    private final RespuestaForoRepository respuestaForoRepository;
    private final PmSolicitudPersonalService pmSolicitudPersonalService;
    private final PenalizacionService penalizacionService;
    private final RmTalentMatchingService rmTalentMatchingService;

    public PmAsistenteContextoService(UsuarioRepository usuarioRepository,
                                      ProyectoRepository proyectoRepository,
                                      AsignacionRepository asignacionRepository,
                                      ActividadRepository actividadRepository,
                                      PublicacionForoRepository publicacionForoRepository,
                                      RespuestaForoRepository respuestaForoRepository,
                                      PmSolicitudPersonalService pmSolicitudPersonalService,
                                      PenalizacionService penalizacionService,
                                      RmTalentMatchingService rmTalentMatchingService) {
        this.usuarioRepository = usuarioRepository;
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.actividadRepository = actividadRepository;
        this.publicacionForoRepository = publicacionForoRepository;
        this.respuestaForoRepository = respuestaForoRepository;
        this.pmSolicitudPersonalService = pmSolicitudPersonalService;
        this.penalizacionService = penalizacionService;
        this.rmTalentMatchingService = rmTalentMatchingService;
    }

    @Transactional(readOnly = true)
    public KpisAsistente obtenerKpis(Usuario pm) {
        List<Proyecto> misProyectos = proyectoRepository.findByPmOrderByFechaCreacionDesc(pm);
        Set<Long> colaboradoresUnicos = new HashSet<>();
        int vacantes = 0;
        int pendientesPm = 0;

        for (Proyecto p : misProyectos) {
            List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(p, EstadoAsignacion.ACTIVA);
            for (Asignacion a : activas) {
                colaboradoresUnicos.add(a.getColaborador().getId());
            }
            if (p.getEstado() == EstadoProyecto.ACTIVO || p.getEstado() == EstadoProyecto.EN_ESPERA
                    || p.getEstado() == EstadoProyecto.EN_REVISION) {
                vacantes += Math.max(0, p.getColaboradoresRequeridos() - activas.size());
            }
            pendientesPm += asignacionRepository.findPendientesPmByProyecto(p).size();
            pendientesPm += (int) actividadRepository.countByProyectoAndEstadoAndActivoTrue(
                    p, EstadoActividad.EN_REVISION);
        }
        return new KpisAsistente(misProyectos.size(), colaboradoresUnicos.size(), vacantes, pendientesPm);
    }

    @Transactional(readOnly = true)
    public List<ConsultaRapida> listarConsultasRapidas(Usuario pm, Long proyectoId) {
        Proyecto enfocado = null;
        if (proyectoId != null) {
            enfocado = proyectoRepository.findById(proyectoId)
                    .filter(p -> p.getPm().getId().equals(pm.getId()))
                    .orElse(null);
        }
        if (enfocado != null) {
            String n = enfocado.getNombre();
            return List.of(
                    new ConsultaRapida("Analizar equipo", "Analiza el equipo y desempeño de " + n),
                    new ConsultaRapida("Detectar habilidades faltantes", "¿Qué habilidades faltan cubrir en " + n + "?"),
                    new ConsultaRapida("Recomendar candidatos", "Recomienda candidatos disponibles para proponer a " + n),
                    new ConsultaRapida("Evaluar carga y disponibilidad", "Evalúa la disponibilidad y carga horaria del equipo en " + n),
                    new ConsultaRapida("Revisar pendientes", "Resume mis aprobaciones y revisiones pendientes en " + n),
                    new ConsultaRapida("Identificar riesgos", "Identifica riesgos, tareas vencidas y strikes en " + n)
            );
        }
        return List.of(
                new ConsultaRapida("Analizar equipo", "Analiza el desempeño y composición de mis equipos actuales"),
                new ConsultaRapida("Detectar habilidades faltantes", "¿Qué habilidades faltan cubrir en mis proyectos activos?"),
                new ConsultaRapida("Recomendar candidatos", "Recomienda candidatos disponibles para proponer a mis proyectos con vacantes"),
                new ConsultaRapida("Evaluar disponibilidad y carga", "Evalúa la disponibilidad y carga horaria de mi equipo"),
                new ConsultaRapida("Revisar pendientes", "Resume mis aprobaciones de asignaciones y entregas en revisión pendientes"),
                new ConsultaRapida("Identificar riesgos", "Identifica riesgos, atrasos y strikes en mis proyectos activos")
        );
    }

    @Transactional(readOnly = true)
    public ContextoPm construir(Long pmId, Long proyectoContextoId, String mensaje) {
        Usuario pm = usuarioRepository.findById(pmId)
                .orElseThrow(() -> new IllegalArgumentException("No encontramos tu usuario PM."));
        String mensajeN = normalizar(mensaje);
        List<String> palabras = extraerPalabras(mensajeN);

        List<Proyecto> todosMisProyectos = proyectoRepository.findByPmOrderByFechaCreacionDesc(pm);
        KpisAsistente kpis = obtenerKpis(pm);

        Proyecto enfocado = resolverProyectoEnfocado(todosMisProyectos, proyectoContextoId, mensajeN);
        List<Proyecto> proyectosAnalizados = enfocado != null
                ? List.of(enfocado)
                : todosMisProyectos;

        Map<Long, ProyectoTarjeta> tarjetasProyectos = new LinkedHashMap<>();
        Map<String, CandidatoTarjeta> tarjetasCandidatos = new LinkedHashMap<>();
        Map<Long, Fuente> publicaciones = new LinkedHashMap<>();

        StringBuilder t = new StringBuilder();
        t.append("## RESUMEN DEL PORTAFOLIO DEL PROJECT MANAGER\n");
        t.append("PM: ").append(dato(pm.getNombre() + " " + pm.getApellido())).append('\n');
        t.append("Proyectos a su cargo: ").append(kpis.totalProyectos())
                .append(" | Colaboradores activos únicos: ").append(kpis.totalColaboradores())
                .append(" | Vacantes abiertas: ").append(kpis.totalVacantes())
                .append(" | Pendientes del PM (asignaciones + revisiones): ").append(kpis.totalPendientesPm()).append('\n');
        if (enfocado != null) {
            t.append("Contexto enfocado en el proyecto: ").append(dato(enfocado.getNombre()))
                    .append(" [[PROY:").append(enfocado.getId()).append("]]\n");
        }

        LocalDate hoy = LocalDate.now();
        List<String> lineasBrechas = new ArrayList<>();
        List<String> lineasEquipo = new ArrayList<>();
        List<String> lineasPendientes = new ArrayList<>();
        List<String> lineasRiesgos = new ArrayList<>();

        t.append("\n## PROYECTOS DEL PM Y ESTADO DETALLADO\n");
        if (todosMisProyectos.isEmpty()) {
            t.append("El PM aún no tiene proyectos creados.\n");
        }

        for (Proyecto p : todosMisProyectos) {
            List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(p, EstadoAsignacion.ACTIVA);
            List<Asignacion> pendientesAsig = asignacionRepository.findPendientesPmByProyecto(p);
            List<Actividad> actividades = actividadRepository.findByProyectoAndActivoTrue(p);

            int totalAct = actividades.size();
            int completadas = (int) actividades.stream().filter(a -> a.getEstado() == EstadoActividad.COMPLETADA).count();
            int enRevision = (int) actividades.stream().filter(a -> a.getEstado() == EstadoActividad.EN_REVISION).count();
            int vencidas = (int) actividades.stream()
                    .filter(a -> a.getEstado() != EstadoActividad.COMPLETADA
                            && a.getFechaLimite() != null && a.getFechaLimite().isBefore(hoy))
                    .count();
            int aTiempo = (int) actividades.stream().filter(a -> a.getEstadoEntrega() == EstadoEntrega.A_TIEMPO).count();
            int tardias = (int) actividades.stream().filter(a -> a.getEstadoEntrega() == EstadoEntrega.TARDIA).count();
            int entregas = aTiempo + tardias;
            int puntualidad = entregas == 0 ? 0 : Math.round(aTiempo * 100f / entregas);
            int progreso = totalAct == 0 ? 0 : (int) Math.round((completadas * 100.0) / totalAct);
            int vacantesProy = Math.max(0, p.getColaboradoresRequeridos() - activas.size());

            ProyectoTarjeta tarjeta = new ProyectoTarjeta(
                    p.getId(),
                    p.getNombre(),
                    p.getEstado().name(),
                    p.getPrioridad() != null ? p.getPrioridad().name() : "MEDIA",
                    activas.size() + " / " + p.getColaboradoresRequeridos(),
                    progreso,
                    vacantesProy,
                    pendientesAsig.size() + enRevision,
                    "/pm/proyectos/detalle?id=" + p.getId(),
                    "/pm/actividades?proyectoId=" + p.getId(),
                    "/pm/reportes/detalle?proyectoId=" + p.getId()
            );
            tarjetasProyectos.put(p.getId(), tarjeta);

            if (enfocado != null && !enfocado.getId().equals(p.getId())) {
                continue;
            }

            BigDecimal presupuestoVisible = p.getPresupuesto() != null
                    ? p.getPresupuesto() : p.getPresupuestoSolicitado();
            t.append("\n### Proyecto [[PROY:").append(p.getId()).append("]] ").append(dato(p.getNombre())).append('\n');
            t.append("- Estado: ").append(p.getEstado()).append(" | Prioridad: ").append(p.getPrioridad())
                    .append(" | Fechas: ").append(p.getFechaInicio() != null ? p.getFechaInicio() : "por definir")
                    .append(" a ").append(p.getFechaFinEstimada() != null ? p.getFechaFinEstimada() : "por definir").append('\n');
            t.append("- Presupuesto del proyecto: S/ ").append(presupuestoVisible != null ? presupuestoVisible : BigDecimal.ZERO)
                    .append(" | Equipo activo: ").append(activas.size()).append('/').append(p.getColaboradoresRequeridos())
                    .append(" (vacantes: ").append(vacantesProy).append(")\n");
            t.append("- Avance de actividades: ").append(completadas).append('/').append(totalAct)
                    .append(" completadas (").append(progreso).append("%) | En revisión del PM: ").append(enRevision)
                    .append(" | Vencidas sin completar: ").append(vencidas)
                    .append(" | Puntualidad: ").append(puntualidad).append("%\n");

            // Brechas de habilidades por proyecto
            List<PmCupoHabilidadView> cupos = pmSolicitudPersonalService.listarCupos(p);
            if (!cupos.isEmpty()) {
                t.append("- Perfiles/habilidades requeridas en el proyecto:\n");
                for (PmCupoHabilidadView cupo : cupos) {
                    t.append("  * ").append(dato(cupo.getHabilidad())).append(" (").append(cupo.getNivel())
                            .append("): cubiertos ").append(cupo.getOcupados()).append('/').append(cupo.getExigidos())
                            .append(", faltan ").append(cupo.getDisponibles()).append(" cupo(s)\n");
                    if (cupo.getDisponibles() > 0 && (p.getEstado() == EstadoProyecto.ACTIVO
                            || p.getEstado() == EstadoProyecto.EN_ESPERA || p.getEstado() == EstadoProyecto.EN_REVISION)) {
                        lineasBrechas.add(p.getNombre() + ": falta(n) " + cupo.getDisponibles() + " cupo(s) de "
                                + cupo.getHabilidad() + " (" + cupo.getNivel() + ").");
                    }
                }
            } else if (vacantesProy > 0) {
                lineasBrechas.add(p.getNombre() + ": tiene " + vacantesProy + " vacante(s) por cubrir.");
            }

            // Equipo activo y carga
            if (!activas.isEmpty()) {
                t.append("- Integrantes activos y carga:\n");
                for (Asignacion a : activas) {
                    Usuario col = a.getColaborador();
                    List<Actividad> suyas = actividades.stream()
                            .filter(act -> act.getColaborador().getId().equals(col.getId()))
                            .toList();
                    long compCol = suyas.stream().filter(act -> act.getEstado() == EstadoActividad.COMPLETADA).count();
                    long vencCol = suyas.stream()
                            .filter(act -> act.getEstado() != EstadoActividad.COMPLETADA
                                    && act.getFechaLimite() != null && act.getFechaLimite().isBefore(hoy))
                            .count();
                    int devCol = suyas.stream().mapToInt(Actividad::getVecesDevuelta).sum();
                    int strikes = penalizacionService.contarStrikes(col, p);
                    BigDecimal disp = col.getHorasDisponibles() != null ? col.getHorasDisponibles() : BigDecimal.ZERO;

                    String resumenMiembro = col.getNombre() + " " + col.getApellido() + " en " + p.getNombre()
                            + ": " + a.getHorasSemanales() + " h/sem asignadas (" + disp + " h/sem libres), "
                            + compCol + "/" + suyas.size() + " tareas completadas, "
                            + vencCol + " vencida(s), " + devCol + " devolución(es), strikes " + strikes + "/3.";
                    lineasEquipo.add(resumenMiembro);
                    t.append("  * ").append(dato(col.getNombre() + " " + col.getApellido()))
                            .append(" en ").append(dato(p.getNombre()))
                            .append(": ").append(a.getHorasSemanales()).append(" h/sem asignadas (").append(disp)
                            .append(" h/sem libres), ").append(compCol).append('/').append(suyas.size())
                            .append(" tareas completadas, ").append(vencCol).append(" vencida(s), ")
                            .append(devCol).append(" devolución(es), strikes ").append(strikes).append("/3.\n");

                    if (strikes >= 2) {
                        lineasRiesgos.add("Alerta de strikes en " + p.getNombre() + ": " + col.getNombre() + " "
                                + col.getApellido() + " acumula " + strikes + "/3 strikes.");
                    }
                    if (vencCol > 0) {
                        lineasRiesgos.add("Tareas vencidas en " + p.getNombre() + ": " + col.getNombre() + " "
                                + col.getApellido() + " tiene " + vencCol + " actividad(es) vencida(s) sin completar.");
                    }
                }
            }

            // Pendientes del PM
            for (Asignacion pend : pendientesAsig) {
                Usuario col = pend.getColaborador();
                String lineaPend = "Asignación pendiente del PM en " + p.getNombre() + ": "
                        + col.getNombre() + " " + col.getApellido() + " (" + pend.getHorasSemanales()
                        + " h/sem, origen " + pend.getOrigen() + ").";
                lineasPendientes.add(lineaPend);
                t.append("  * PENDIENTE APROBACIÓN PM en ").append(dato(p.getNombre())).append(": ")
                        .append(dato(col.getNombre() + " " + col.getApellido())).append(" (")
                        .append(pend.getHorasSemanales()).append(" h/sem, origen ").append(pend.getOrigen()).append(").\n");
            }
            for (Actividad actRev : actividades) {
                if (actRev.getEstado() == EstadoActividad.EN_REVISION) {
                    String lineaRev = "Actividad en revisión en " + p.getNombre() + ": \"" + actRev.getTitulo()
                            + "\" entregada por " + actRev.getColaborador().getNombre() + " "
                            + actRev.getColaborador().getApellido() + ".";
                    lineasPendientes.add(lineaRev);
                    t.append("  * ACTIVIDAD EN REVISIÓN en ").append(dato(p.getNombre())).append(": ")
                            .append(dato(actRev.getTitulo())).append(" entregada por ")
                            .append(dato(actRev.getColaborador().getNombre() + " " + actRev.getColaborador().getApellido()))
                            .append(".\n");
                }
            }

            if (p.getEstado() == EstadoProyecto.ACTIVO && vacantesProy > 0) {
                lineasRiesgos.add("Capacidad incompleta en " + p.getNombre() + ": faltan " + vacantesProy
                        + " colaborador(es) para completar el equipo requerido.");
            }
            if (p.getEstado() == EstadoProyecto.ACTIVO && p.getFechaFinEstimada() != null
                    && !p.getFechaFinEstimada().isAfter(hoy.plusDays(14)) && progreso < 80) {
                lineasRiesgos.add("Plazo próximo en " + p.getNombre() + ": fecha fin estimada "
                        + p.getFechaFinEstimada() + " con avance actual de " + progreso + "%.");
            }
        }

        // Talent matching para recomendar candidatos a proyectos con vacantes
        agregarCandidatosRecomendados(t, proyectosAnalizados, tarjetasCandidatos);

        // Búsqueda en foros accesibles por el PM
        agregarForosPm(t, pm, palabras, publicaciones);

        String resumenLocal = construirRespuestaAnaliticaLocal(
                mensajeN, enfocado, kpis, proyectosAnalizados, tarjetasProyectos,
                lineasEquipo, lineasBrechas, lineasPendientes, lineasRiesgos, tarjetasCandidatos);

        return new ContextoPm(
                t.toString(),
                resumenLocal,
                enfocado,
                kpis,
                tarjetasProyectos,
                tarjetasCandidatos,
                publicaciones
        );
    }

    private Proyecto resolverProyectoEnfocado(List<Proyecto> proyectos, Long proyectoContextoId, String mensajeN) {
        if (proyectoContextoId != null) {
            for (Proyecto p : proyectos) {
                if (p.getId().equals(proyectoContextoId)) {
                    return p;
                }
            }
        }
        Proyecto mejor = null;
        for (Proyecto p : proyectos) {
            String nombre = normalizar(p.getNombre());
            if (!nombre.isBlank() && mensajeN.contains(nombre)) {
                if (mejor == null || nombre.length() > normalizar(mejor.getNombre()).length()) {
                    mejor = p;
                }
            }
        }
        return mejor;
    }

    private void agregarCandidatosRecomendados(StringBuilder t,
                                               List<Proyecto> proyectosAnalizados,
                                               Map<String, CandidatoTarjeta> tarjetasCandidatos) {
        t.append("\n## CANDIDATOS RECOMENDADOS DISPONIBLES (HABILIDADES VALIDADAS POR RM, SIN DATOS SALARIALES)\n");
        List<Proyecto> asignables = proyectosAnalizados.stream()
                .filter(p -> p.getEstado() == EstadoProyecto.ACTIVO || p.getEstado() == EstadoProyecto.EN_ESPERA)
                .toList();
        if (asignables.isEmpty()) {
            t.append("No hay proyectos activos o en espera seleccionados para proponer asignaciones.\n");
            return;
        }

        int totalRecomendados = 0;

        for (Proyecto p : asignables) {
            int activas = asignacionRepository.findByProyectoAndEstado(p, EstadoAsignacion.ACTIVA).size();
            if (p.getColaboradoresRequeridos() - activas <= 0) {
                // Igual que Talent Matching del RM: un proyecto sin vacantes no recibe recomendaciones.
                continue;
            }
            // Mismo ranking y porcentaje que ve el RM. Por la decisión S18, el PM no recibe costos ni presupuesto.
            List<HabilidadFaltante> faltantes = rmTalentMatchingService.calcularHabilidadesFaltantes(p);
            for (RecomendacionCandidato rec : rmTalentMatchingService.recomendarParaProyecto(p, faltantes)) {
                List<String> habilidades = rec.habilidadesQueCubre().isEmpty()
                        ? rec.habilidadesValidadas() : rec.habilidadesQueCubre();
                CandidatoTarjeta cand = new CandidatoTarjeta(
                        rec.colaboradorId(),
                        rec.nombre(),
                        rec.cargo(),
                        rec.nivel(),
                        rec.horasDisponibles(),
                        String.join(", ", habilidades),
                        rec.matchPorcentaje(),
                        p.getId(),
                        p.getNombre(),
                        rec.horasSugeridas(),
                        true
                );
                String clave = cand.colaboradorId() + ":" + cand.proyectoId();
                tarjetasCandidatos.put(clave, cand);
                t.append("- [[CAND:").append(clave).append("]] ").append(dato(cand.nombre()))
                        .append(" | cargo: ").append(dato(cand.cargo()))
                        .append(" | nivel: ").append(cand.nivelExperiencia())
                        .append(" | horas disponibles: ").append(cand.horasDisponibles()).append(" h/sem")
                        .append(" | habilidades validadas que aplican a ").append(dato(cand.proyectoNombre()))
                        .append(": ").append(dato(cand.habilidadesMatch()));
                if (!rec.notasNivel().isEmpty()) {
                    t.append(" | notas de nivel: ").append(dato(String.join("; ", rec.notasNivel())));
                }
                t.append(" | match (mismo cálculo que Talent Matching del RM): ")
                        .append(cand.puntajeAfinidad()).append("%\n");
                totalRecomendados++;
            }
        }

        if (totalRecomendados == 0) {
            t.append("No se encontraron candidatos disponibles con habilidades validadas que coincidan con las vacantes actuales.\n");
        }
    }

    private void agregarForosPm(StringBuilder t, Usuario pm, List<String> palabras, Map<Long, Fuente> publicaciones) {
        t.append("\n## BÚSQUEDA EN LOS FOROS DE SKILLBRIDGE ACCESIBLES AL PM\n");
        if (palabras.isEmpty()) {
            t.append("Sin términos específicos para búsqueda en foros.\n");
            return;
        }
        int limite = Math.min(palabras.size(), 5);
        Map<Long, PublicacionForo> candidatas = new LinkedHashMap<>();

        for (int i = 0; i < limite; i++) {
            String clave = palabras.get(i);
            for (PublicacionForo p : publicacionForoRepository.buscarPorPalabra(clave, PageRequest.of(0, 20))) {
                Foro f = p.getForo();
                boolean acceso = f.isVisibleParaTodos()
                        || (f.getProyecto() != null && f.getProyecto().getPm().getId().equals(pm.getId()));
                if (acceso) {
                    candidatas.putIfAbsent(p.getId(), p);
                }
            }
        }
        int mostrados = 0;
        for (PublicacionForo p : candidatas.values()) {
            if (mostrados >= MAX_PUBLICACIONES) break;
            Foro foro = p.getForo();
            RespuestaForo solucion = respuestaForoRepository.findByPublicacionAndEsSolucionTrue(p).orElse(null);
            boolean resuelta = solucion != null && solucion.isActivo();
            String url = "/pm/foro/detalle?foroId=" + foro.getId();
            publicaciones.put(p.getId(), new Fuente(p.getId(), p.getTitulo(), foro.getNombre(), url, resuelta));
            t.append("- [[PUB:").append(p.getId()).append("]] ").append(dato(p.getTitulo()))
                    .append(" en ").append(dato(foro.getNombre()))
                    .append(" (resuelta: ").append(resuelta ? "sí" : "no").append("): ")
                    .append(extractoForo(cortar(textoPlano(p.getContenido(), 300), 220))).append('\n');
            mostrados++;
        }
        if (mostrados == 0) {
            t.append("Sin publicaciones relevantes en los foros para esta consulta.\n");
        }
    }

    private String construirRespuestaAnaliticaLocal(
            String mensajeN,
            Proyecto enfocado,
            KpisAsistente kpis,
            List<Proyecto> proyectosAnalizados,
            Map<Long, ProyectoTarjeta> tarjetasProyectos,
            List<String> lineasEquipo,
            List<String> lineasBrechas,
            List<String> lineasPendientes,
            List<String> lineasRiesgos,
            Map<String, CandidatoTarjeta> tarjetasCandidatos) {

        StringBuilder r = new StringBuilder();
        String alcance = enfocado != null
                // El marcador ya se muestra con el nombre del proyecto: escribir también el nombre lo repetía.
                ? "del proyecto [[PROY:" + enfocado.getId() + "]]"
                : "de tu portafolio actual (" + kpis.totalProyectos() + " proyecto(s))";

        boolean pideCandidatos = mensajeN.contains("candidat") || mensajeN.contains("recomiend")
                || mensajeN.contains("proponer") || mensajeN.contains("talento") || mensajeN.contains("vacante");
        boolean pideHabilidades = mensajeN.contains("habilidad") || mensajeN.contains("falta")
                || mensajeN.contains("brecha") || mensajeN.contains("perfil") || mensajeN.contains("cupo");
        boolean pideEquipo = mensajeN.contains("equipo") || mensajeN.contains("carga")
                || mensajeN.contains("disponibilidad") || mensajeN.contains("desempeno") || mensajeN.contains("horas");
        boolean pidePendientes = mensajeN.contains("pendiente") || mensajeN.contains("aprobacion")
                || mensajeN.contains("revision") || mensajeN.contains("aprobar");
        boolean pideRiesgos = mensajeN.contains("riesgo") || mensajeN.contains("atraso")
                || mensajeN.contains("vencid") || mensajeN.contains("strike") || mensajeN.contains("alerta");

        r.append("**Según SkillBridge**\n");
        r.append("Aquí tienes el análisis actualizado ").append(alcance).append(":\n");

        if (pideCandidatos) {
            if (tarjetasCandidatos.isEmpty()) {
                r.append("- Actualmente no hay candidatos disponibles con habilidades validadas por el RM que coincidan con las vacantes abiertas.\n");
                r.append("- Puedes enviar una Solicitud de Personal al Resource Manager desde el detalle del proyecto.\n");
            } else {
                r.append("- He identificado ").append(tarjetasCandidatos.size())
                        .append(" candidato(s) con habilidades validadas por el RM y disponibilidad horaria:\n");
                for (Map.Entry<String, CandidatoTarjeta> entry : tarjetasCandidatos.entrySet()) {
                    CandidatoTarjeta c = entry.getValue();
                    r.append("- [[CAND:").append(entry.getKey()).append("]] — ").append(c.horasDisponibles())
                            .append(" h/sem disponibles | Habilidades validadas: ").append(c.habilidadesMatch())
                            .append(" | Afinidad: ").append(c.puntajeAfinidad()).append("%.\n");
                }
                r.append("- Recuerda que las recomendaciones no asignan directamente: usa el botón **Proponer al proyecto** en la tarjeta del candidato para enviar la propuesta al flujo de aprobación del RM.\n");
            }
            return r.toString();
        }

        if (pideHabilidades) {
            if (lineasBrechas.isEmpty()) {
                r.append("- Todos los perfiles y cupos de habilidades requeridas están cubiertos actualmente.\n");
            } else {
                for (String b : lineasBrechas) {
                    r.append("- ").append(b).append('\n');
                }
            }
            if (!tarjetasCandidatos.isEmpty()) {
                r.append("- Candidatos disponibles que pueden cubrir estas brechas:\n");
                int n = 0;
                for (Map.Entry<String, CandidatoTarjeta> entry : tarjetasCandidatos.entrySet()) {
                    if (n++ >= 3) break;
                    r.append("- [[CAND:").append(entry.getKey()).append("]] (").append(entry.getValue().habilidadesMatch()).append(")\n");
                }
            }
            return r.toString();
        }

        if (pideEquipo) {
            if (lineasEquipo.isEmpty()) {
                r.append("- No hay colaboradores con asignación activa en el alcance consultado.\n");
            } else {
                for (String eq : lineasEquipo) {
                    r.append("- ").append(eq).append('\n');
                }
            }
            return r.toString();
        }

        if (pidePendientes) {
            if (lineasPendientes.isEmpty()) {
                r.append("- No tienes asignaciones pendientes de aprobación ni actividades en revisión en este momento.\n");
            } else {
                for (String pend : lineasPendientes) {
                    r.append("- ").append(pend).append('\n');
                }
            }
            return r.toString();
        }

        if (pideRiesgos) {
            if (lineasRiesgos.isEmpty()) {
                r.append("- No se detectan alertas críticas (sin actividades vencidas ni colaboradores con 2+ strikes).\n");
            } else {
                for (String riesgo : lineasRiesgos) {
                    r.append("- ").append(riesgo).append('\n');
                }
            }
            return r.toString();
        }

        // Resumen integral por defecto
        for (Proyecto p : proyectosAnalizados) {
            ProyectoTarjeta pt = tarjetasProyectos.get(p.getId());
            if (pt != null) {
                r.append("- Proyecto [[PROY:").append(p.getId()).append("]]: estado ").append(pt.estado())
                        .append(", avance ").append(pt.progresoPorc()).append("%, equipo ").append(pt.equipoTexto())
                        .append(", vacantes ").append(pt.vacantes())
                        .append(", pendientes del PM ").append(pt.pendientesRevision()).append(".\n");
            }
        }
        if (!lineasPendientes.isEmpty()) {
            r.append("- Tienes ").append(lineasPendientes.size()).append(" gestión(es) pendiente(s) de revisión o aprobación.\n");
        }
        if (!lineasRiesgos.isEmpty()) {
            r.append("- Alertas detectadas: ").append(lineasRiesgos.get(0)).append('\n');
        }
        return r.toString();
    }

    public static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim();
    }

    private List<String> extraerPalabras(String mensajeN) {
        String[] partes = mensajeN.split("[^a-z0-9+#.]+");
        List<String> palabras = new ArrayList<>();
        for (String p : partes) {
            String limpia = p.replaceAll("^[.]+|[.]+$", "");
            if (limpia.length() >= 3 && !IGNORAR.contains(limpia) && !palabras.contains(limpia)) {
                palabras.add(limpia);
            }
        }
        palabras.sort((a, b) -> b.length() - a.length());
        return palabras;
    }

    private String textoPlano(String html, int maximo) {
        if (html == null) return "";
        String texto = Jsoup.parse(html).text();
        return texto.length() > maximo ? texto.substring(0, maximo) : texto;
    }

    private String cortar(String s, int maximo) {
        if (s == null) return "";
        return s.length() > maximo ? s.substring(0, maximo) + "..." : s;
    }

    private String dato(String s) {
        return AsistenteSeguridadService.envolverDato(s);
    }

    private String extractoForo(String s) {
        return AsistenteSeguridadService.envolverExtractoForo(s);
    }

    private String limpiar(String s) {
        return AsistenteSeguridadService.sanitizarDato(s);
    }
}
