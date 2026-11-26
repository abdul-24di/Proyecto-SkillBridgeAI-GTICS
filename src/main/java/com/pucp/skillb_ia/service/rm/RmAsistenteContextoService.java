package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmAsignacionView;
import com.pucp.skillb_ia.dto.RmAsistenteRespuesta.CandidatoTarjetaRm;
import com.pucp.skillb_ia.dto.RmAsistenteRespuesta.ConsultaRapidaRm;
import com.pucp.skillb_ia.dto.RmAsistenteRespuesta.KpisAsistenteRm;
import com.pucp.skillb_ia.dto.RmAsistenteRespuesta.ProyectoTarjetaRm;
import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.dto.RmSolicitudPersonalView;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.service.AsistenteSeguridadService;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.DatosBrechas;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.DatosCandidatos;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.HabilidadFaltante;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.RecomendacionCandidato;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/**
 * Arma los datos que recibe el asistente de IA del RM: KPIs, proyectos visibles, candidatos de Talent
 * Matching con su impacto presupuestal, asignaciones y solicitudes pendientes y validaciones por revisar.
 * El texto escrito por usuarios se marca como dato para la IA. Lo usa {@link RmAsistenteService}.
 */
@Service
public class RmAsistenteContextoService {

    /**
     * Contexto de una consulta al asistente.
     *
     * @param textoPrompt bloque DATOS DE SKILLBRIDGE (RM) que se envía al modelo
     * @param resumenAnaliticoLocal respuesta que se usa cuando la IA no está disponible
     * @param proyectoEnfocado proyecto enfocado, o {@code null}
     * @param kpis indicadores del panel del asistente
     * @param proyectos tarjetas de proyecto por id, para resolver {@code [[PROY:id]]}
     * @param candidatos tarjetas de candidato por {@code colaboradorId:proyectoId}, para resolver {@code [[CAND:...]]}
     */
    public record ContextoRm(
            String textoPrompt,
            String resumenAnaliticoLocal,
            RmProyectoView proyectoEnfocado,
            KpisAsistenteRm kpis,
            Map<Long, ProyectoTarjetaRm> proyectos,
            Map<String, CandidatoTarjetaRm> candidatos) {}

    // Cuántos proyectos recibieron candidatos, cuántos tienen vacantes y si el enfocado tiene los cupos completos.
    private record AlcanceCandidatos(int proyectosAnalizados, int proyectosConVacantes, boolean enfocadoCuposCompletos) {}

    static final int MAX_PROYECTOS_CANDIDATOS = 5;

    private final RmProyectoConsultaService rmProyectoConsultaService;
    private final RmTalentMatchingService rmTalentMatchingService;
    private final RmAsignacionService rmAsignacionService;
    private final RmSolicitudPersonalService rmSolicitudPersonalService;
    private final RmCertificadoService rmCertificadoService;
    private final RmEducacionService rmEducacionService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public RmAsistenteContextoService(RmProyectoConsultaService rmProyectoConsultaService,
                                      RmTalentMatchingService rmTalentMatchingService,
                                      RmAsignacionService rmAsignacionService,
                                      RmSolicitudPersonalService rmSolicitudPersonalService,
                                      RmCertificadoService rmCertificadoService,
                                      RmEducacionService rmEducacionService) {
        this.rmProyectoConsultaService = rmProyectoConsultaService;
        this.rmTalentMatchingService = rmTalentMatchingService;
        this.rmAsignacionService = rmAsignacionService;
        this.rmSolicitudPersonalService = rmSolicitudPersonalService;
        this.rmCertificadoService = rmCertificadoService;
        this.rmEducacionService = rmEducacionService;
    }

    /**
     * Calcula los KPIs del asistente: proyectos visibles, vacantes de los proyectos con dotación abierta,
     * asignaciones que esperan decisión del RM y certificados y formaciones pendientes de validar.
     *
     * @return los KPIs
     */
    @Transactional(readOnly = true)
    public KpisAsistenteRm obtenerKpis() {
        return calcularKpis(rmProyectoConsultaService.listar(), rmAsignacionService.listar(),
                rmCertificadoService.listarPendientes().size() + rmEducacionService.listarPendientes().size());
    }

    private static KpisAsistenteRm calcularKpis(List<RmProyectoView> proyectos, List<RmAsignacionView> asignaciones,
                                                long validacionesPendientes) {
        int vacantes = proyectos.stream()
                .filter(RmProyectoView::isConVacantesParaDotacion)
                .mapToInt(RmProyectoView::getVacantes)
                .sum();
        long asigPendientes = asignaciones.stream()
                .filter(RmAsignacionView::isRequiereDecisionRm)
                .count();
        return new KpisAsistenteRm(proyectos.size(), vacantes, asigPendientes, validacionesPendientes);
    }

    /**
     * Lista los botones de consulta rápida. Con un proyecto válido, las preguntas mencionan ese proyecto;
     * si no existe o no es visible, se usan las preguntas generales.
     *
     * @param proyectoId proyecto enfocado, o {@code null}
     * @return seis consultas rápidas
     */
    @Transactional(readOnly = true)
    public List<ConsultaRapidaRm> listarConsultasRapidas(Long proyectoId) {
        RmProyectoView enfocado = null;
        if (proyectoId != null) {
            try {
                enfocado = rmProyectoConsultaService.obtener(proyectoId);
            } catch (IllegalArgumentException ignorado) {
                enfocado = null;
            }
        }
        if (enfocado != null) {
            String n = enfocado.getProyecto().getNombre();
            return List.of(
                    new ConsultaRapidaRm("Recomendar candidatos", "Recomienda los mejores candidatos para " + n + " y su impacto presupuestal"),
                    new ConsultaRapidaRm("Habilidades faltantes", "¿Qué habilidades faltan cubrir en " + n + "?"),
                    new ConsultaRapidaRm("Evaluar presupuesto", "Analiza el presupuesto asignado, reservado y disponible de " + n),
                    new ConsultaRapidaRm("Revisar asignaciones pendientes", "¿Qué asignaciones o postulaciones requieren mi decisión en " + n + "?"),
                    new ConsultaRapidaRm("Solicitudes del PM", "Revisa las solicitudes de personal del PM para " + n),
                    new ConsultaRapidaRm("Validaciones pendientes", "Resume mis validaciones pendientes de certificados y educación")
            );
        }
        return List.of(
                new ConsultaRapidaRm("Recomendar candidatos", "Recomienda candidatos para los proyectos activos con vacantes y evalúa su costo"),
                new ConsultaRapidaRm("Habilidades faltantes", "¿Qué habilidades faltan cubrir en los proyectos con vacantes?"),
                new ConsultaRapidaRm("Analizar presupuestos", "Analiza el presupuesto comprometido y disponible de los proyectos a mi cargo"),
                new ConsultaRapidaRm("Asignaciones por aprobar", "Resume las propuestas de PM y postulaciones pendientes de mi aprobación como RM"),
                new ConsultaRapidaRm("Solicitudes de personal", "¿Qué solicitudes de personal enviadas por los PMs están pendientes de atención?"),
                new ConsultaRapidaRm("Validaciones pendientes", "Resume los certificados y formaciones académicas pendientes de validar")
        );
    }

    /**
     * Construye el contexto de una consulta. El proyecto enfocado sale del id recibido o del nombre
     * mencionado en el mensaje. Sin enfoque, solo se buscan candidatos para los proyectos con vacantes,
     * por prioridad y hasta 5 proyectos; el enfocado siempre recibe candidatos, aunque tenga los cupos
     * completos.
     *
     * @param proyectoContextoId proyecto enfocado desde la vista, o {@code null}
     * @param mensaje consulta ya limpiada
     * @return el contexto para el prompt y las tarjetas
     */
    @Transactional(readOnly = true)
    public ContextoRm construir(Long proyectoContextoId, String mensaje) {
        String mensajeN = normalizar(mensaje);
        // Cada lista se carga una sola vez por mensaje (antes los KPIs y el cuerpo las pedían por separado).
        List<RmProyectoView> todos = rmProyectoConsultaService.listar();
        List<RmAsignacionView> asignaciones = rmAsignacionService.listar();
        int certsPend = rmCertificadoService.listarPendientes().size();
        int eduPend = rmEducacionService.listarPendientes().size();
        KpisAsistenteRm kpis = calcularKpis(todos, asignaciones, certsPend + eduPend);

        RmProyectoView enfocado = resolverProyectoEnfocado(todos, proyectoContextoId, mensajeN);
        List<RmProyectoView> analizados = enfocado != null ? List.of(enfocado) : todos;

        // TASK-069: sin proyecto enfocado solo se buscan candidatos para los proyectos con vacantes (con tope);
        // el proyecto enfocado siempre recibe candidatos, aunque tenga los cupos completos.
        List<RmProyectoView> conVacantes = todos.stream()
                .filter(v -> esDotable(v.getProyecto()) && v.getVacantes() > 0)
                .sorted(Comparator.comparingInt((RmProyectoView v) -> rangoPrioridad(v.getProyecto()))
                        .thenComparing(RmProyectoView::getVacantes, Comparator.reverseOrder()))
                .toList();
        Set<Long> idsConCandidatos = new HashSet<>();
        if (enfocado != null) {
            if (esDotable(enfocado.getProyecto())) {
                idsConCandidatos.add(enfocado.getProyecto().getId());
            }
        } else {
            conVacantes.stream().limit(MAX_PROYECTOS_CANDIDATOS)
                    .forEach(v -> idsConCandidatos.add(v.getProyecto().getId()));
        }
        boolean enfocadoCuposCompletos = enfocado != null && esDotable(enfocado.getProyecto())
                && enfocado.getVacantes() <= 0;
        // TASK-074: brechas y candidatos se calculan con datos cargados en lote una sola vez por mensaje
        // (consultas fijas, sin importar cuántos proyectos ni cuántos integrantes haya).
        List<Long> idsDescritos = enfocado != null
                ? List.of(enfocado.getProyecto().getId())
                : todos.stream().map(v -> v.getProyecto().getId()).toList();
        DatosCandidatos datosCandidatos = idsDescritos.isEmpty()
                ? null : rmTalentMatchingService.cargarDatosCandidatos();
        DatosBrechas datosBrechas = rmTalentMatchingService.cargarDatosBrechas(idsDescritos);

        Map<Long, ProyectoTarjetaRm> tarjetasProyectos = new LinkedHashMap<>();
        Map<String, CandidatoTarjetaRm> tarjetasCandidatos = new LinkedHashMap<>();

        StringBuilder t = new StringBuilder();
        t.append("## RESUMEN DE GESTIÓN DEL RESOURCE MANAGER (RM)\n");
        t.append("Proyectos visibles para el RM: ").append(kpis.totalProyectos())
                .append(" | Vacantes abiertas totales: ").append(kpis.totalVacantes())
                .append(" | Asignaciones pendientes de decisión del RM: ").append(kpis.asignacionesPendientesRm())
                .append(" | Validaciones pendientes (certificados + educación): ").append(kpis.validacionesPendientesRm()).append('\n');

        List<String> lineasPresupuesto = new ArrayList<>();
        List<String> lineasBrechas = new ArrayList<>();
        List<String> lineasPendientesRm = new ArrayList<>();

        t.append("\n## PROYECTOS, PRESUPUESTO Y VACANTES\n");
        if (enfocado == null && conVacantes.size() > MAX_PROYECTOS_CANDIDATOS) {
            t.append("Candidatos calculados solo para los ").append(MAX_PROYECTOS_CANDIDATOS)
                    .append(" proyectos con vacantes de mayor prioridad (hay ").append(conVacantes.size())
                    .append("). Para otro proyecto, el RM debe nombrarlo en su consulta.\n");
        }
        for (RmProyectoView v : todos) {
            Proyecto p = v.getProyecto();
            var resPres = v.getResumenPresupuesto();
            BigDecimal asig = resPres != null && resPres.total() != null ? resPres.total() : v.getPresupuestoVisible();
            BigDecimal disp = resPres != null && resPres.disponible() != null ? resPres.disponible() : BigDecimal.ZERO;
            BigDecimal comp = resPres != null && resPres.comprometido() != null ? resPres.comprometido() : BigDecimal.ZERO;

            ProyectoTarjetaRm tarjeta = new ProyectoTarjetaRm(
                    p.getId(),
                    p.getNombre(),
                    v.getPmNombre(),
                    v.getEstadoTexto(),
                    v.getPrioridadTexto(),
                    v.getIntegrantesActivos() + " / " + p.getColaboradoresRequeridos(),
                    v.getVacantes(),
                    asig != null ? asig : BigDecimal.ZERO,
                    disp,
                    v.getPendientesRm(),
                    "/rm/proyectos/detalle?id=" + p.getId(),
                    "/rm/talent-matching?proyectoId=" + p.getId(),
                    "/rm/proyectos/buscar-colaboradores?proyectoId=" + p.getId()
            );
            tarjetasProyectos.put(p.getId(), tarjeta);

            if (enfocado != null && !enfocado.getProyecto().getId().equals(p.getId())) {
                continue;
            }

            t.append("\n### Proyecto [[PROY:").append(p.getId()).append("]] ").append(dato(p.getNombre())).append('\n');
            t.append("- PM: ").append(dato(v.getPmNombre()))
                    .append(" | Estado: ").append(v.getEstadoTexto())
                    .append(" | Prioridad: ").append(v.getPrioridadTexto())
                    .append(" | Equipo: ").append(v.getIntegrantesActivos()).append('/').append(p.getColaboradoresRequeridos())
                    .append(" (vacantes: ").append(v.getVacantes()).append(")\n");
            t.append("- Presupuesto aprobado: S/ ").append(asig != null ? asig : BigDecimal.ZERO)
                    .append(" | Comprometido (activas + pendientes RM): S/ ").append(comp)
                    .append(" | Saldo disponible: S/ ").append(disp).append('\n');

            lineasPresupuesto.add(p.getNombre() + " (PM: " + v.getPmNombre() + "): presupuesto S/ "
                    + (asig != null ? asig : BigDecimal.ZERO) + ", comprometido S/ " + comp
                    + ", saldo disponible S/ " + disp + " (" + v.getVacantes() + " vacante(s)).");

            List<HabilidadFaltante> faltantes =
                    rmTalentMatchingService.calcularHabilidadesFaltantes(p, datosBrechas, datosCandidatos);
            if (!faltantes.isEmpty()) {
                t.append("- Habilidades faltantes en el proyecto:\n");
                for (HabilidadFaltante hf : faltantes) {
                    String detalleBrecha = p.getNombre() + ": falta(n) " + hf.faltantes() + " cupo(s) de "
                            + hf.nombre() + " (" + hf.nivelRequerido() + ").";
                    lineasBrechas.add(detalleBrecha);
                    t.append("  * Faltan ").append(hf.faltantes()).append(" cupo(s) de ").append(dato(hf.nombre()))
                            .append(" (").append(hf.nivelRequerido()).append(").\n");
                }
            }

            if (idsConCandidatos.contains(p.getId())) {
                if (v.getVacantes() <= 0) {
                    t.append("- Cupos completos: proponer a un candidato exige registrar una justificación de cupo.\n");
                }
                List<RecomendacionCandidato> recs = rmTalentMatchingService.recomendarParaProyecto(p, faltantes, datosCandidatos);
                int tope = Math.min(3, recs.size());
                for (int i = 0; i < tope; i++) {
                    RecomendacionCandidato rc = recs.get(i);
                    String clave = rc.colaboradorId() + ":" + p.getId();
                    CandidatoTarjetaRm candRm = new CandidatoTarjetaRm(
                            rc.colaboradorId(),
                            rc.nombre(),
                            rc.cargo(),
                            rc.nivel(),
                            rc.horasDisponibles(),
                            rc.asignacionesActivas(),
                            rc.maxAsignaciones(),
                            rc.habilidadesQueCubre().isEmpty()
                                    ? String.join(", ", rc.habilidadesValidadas())
                                    : String.join(", ", rc.habilidadesQueCubre()),
                            rc.matchPorcentaje(),
                            p.getId(),
                            p.getNombre(),
                            rc.horasSugeridas(),
                            rc.costoTotalEstimado(),
                            rc.cabeEnPresupuesto(),
                            true
                    );
                    tarjetasCandidatos.put(clave, candRm);
                    t.append("  * Candidato recomendado [[CAND:").append(clave).append("]] ")
                            .append(dato(rc.nombre())).append(" | Match: ").append(rc.matchPorcentaje()).append("%")
                            .append(" | Cubre: ").append(dato(candRm.habilidadesMatch()))
                            .append(" | Disponibilidad: ").append(rc.horasDisponibles()).append(" h/sem")
                            .append(" | Costo estimado: S/ ").append(rc.costoTotalEstimado())
                            .append(" (cabe en presupuesto: ").append(rc.cabeEnPresupuesto() ? "sí" : "no").append(")\n");
                }
            }
        }

        // Asignaciones y solicitudes pendientes del RM
        t.append("\n## BANDEJA DE PENDIENTES DEL RM\n");
        for (RmAsignacionView asig : asignaciones) {
            if (!asig.isRequiereDecisionRm()) continue;
            if (enfocado != null && !asig.getAsignacion().getProyecto().getId().equals(enfocado.getProyecto().getId())) {
                continue;
            }
            String proyectoNombre = asig.getAsignacion().getProyecto().getNombre();
            String detalle = " (" + asig.getAsignacion().getHorasSemanales() + " h/sem, " + asig.getEstadoBandeja() + ").";
            lineasPendientesRm.add("Asignación #" + asig.getAsignacion().getId() + " en "
                    + proyectoNombre + ": " + asig.getColaboradorNombre() + detalle);
            t.append("- Asignación #").append(asig.getAsignacion().getId()).append(" en ").append(dato(proyectoNombre))
                    .append(": ").append(dato(asig.getColaboradorNombre())).append(limpiar(detalle)).append('\n');
        }

        var solicitudes = rmSolicitudPersonalService.listarPagina(
                rmSolicitudPersonalService.normalizarFiltros(null, "PENDIENTE", null), "1").filas();
        for (RmSolicitudPersonalView sol : solicitudes) {
            if (enfocado != null && !sol.getProyecto().getProyecto().getId().equals(enfocado.getProyecto().getId())) {
                continue;
            }
            String proyectoNombre = sol.getSolicitud().getProyecto().getNombre();
            String cantidad = ": solicita " + sol.getSolicitud().getCantidadColaboradores() + " colaborador(es).";
            lineasPendientesRm.add("Solicitud de personal " + sol.getCodigo() + " de " + sol.getPmNombre()
                    + " para " + proyectoNombre + cantidad);
            t.append("- Solicitud de personal ").append(limpiar(sol.getCodigo())).append(" de ").append(dato(sol.getPmNombre()))
                    .append(" para ").append(dato(proyectoNombre)).append(cantidad).append('\n');
        }

        t.append("- Certificados pendientes de validación del RM: ").append(certsPend).append('\n');
        t.append("- Formaciones académicas pendientes de validación del RM: ").append(eduPend).append('\n');

        String resumenLocal = construirRespuestaAnaliticaLocal(
                mensajeN, enfocado, kpis, analizados, tarjetasProyectos,
                lineasPresupuesto, lineasBrechas, lineasPendientesRm, tarjetasCandidatos, certsPend, eduPend,
                new AlcanceCandidatos(idsConCandidatos.size(), conVacantes.size(), enfocadoCuposCompletos));

        return new ContextoRm(
                t.toString(),
                resumenLocal,
                enfocado,
                kpis,
                tarjetasProyectos,
                tarjetasCandidatos
        );
    }

    private RmProyectoView resolverProyectoEnfocado(List<RmProyectoView> todos, Long proyectoId, String mensajeN) {
        if (proyectoId != null) {
            for (RmProyectoView v : todos) {
                if (v.getProyecto().getId().equals(proyectoId)) {
                    return v;
                }
            }
        }
        RmProyectoView mejor = null;
        for (RmProyectoView v : todos) {
            String nombre = normalizar(v.getProyecto().getNombre());
            if (!nombre.isBlank() && mensajeN.contains(nombre)) {
                if (mejor == null || nombre.length() > normalizar(mejor.getProyecto().getNombre()).length()) {
                    mejor = v;
                }
            }
        }
        return mejor;
    }

    private String construirRespuestaAnaliticaLocal(
            String mensajeN,
            RmProyectoView enfocado,
            KpisAsistenteRm kpis,
            List<RmProyectoView> analizados,
            Map<Long, ProyectoTarjetaRm> tarjetasProyectos,
            List<String> lineasPresupuesto,
            List<String> lineasBrechas,
            List<String> lineasPendientesRm,
            Map<String, CandidatoTarjetaRm> tarjetasCandidatos,
            int certsPend,
            int eduPend,
            AlcanceCandidatos alcanceCandidatos) {

        StringBuilder r = new StringBuilder();
        String alcance = enfocado != null
                // El marcador ya se muestra con el nombre del proyecto: escribir también el nombre lo repetía.
                ? "del proyecto [[PROY:" + enfocado.getProyecto().getId() + "]]"
                : "de los proyectos bajo tu gestión (" + kpis.totalProyectos() + " proyecto(s))";

        boolean pideCandidatos = mensajeN.contains("candidat") || mensajeN.contains("recomiend")
                || mensajeN.contains("matching") || mensajeN.contains("talento");
        boolean pideHabilidades = mensajeN.contains("habilidad") || mensajeN.contains("falta")
                || mensajeN.contains("brecha");
        boolean pidePresupuesto = mensajeN.contains("presupuesto") || mensajeN.contains("costo")
                || mensajeN.contains("saldo") || mensajeN.contains("comprometido");
        boolean pideValidaciones = mensajeN.contains("validacion") || mensajeN.contains("certificado")
                || mensajeN.contains("educacion") || mensajeN.contains("formacion");
        boolean pidePendientes = mensajeN.contains("pendiente") || mensajeN.contains("aprobar")
                || mensajeN.contains("solicitud") || mensajeN.contains("decision");

        r.append("**Según SkillBridge (Resource Manager)**\n");
        r.append("Aquí tienes el análisis en tiempo real ").append(alcance).append(":\n");

        if (pideCandidatos) {
            if (tarjetasCandidatos.isEmpty()) {
                if (enfocado != null && !esDotable(enfocado.getProyecto())) {
                    r.append("- El proyecto está en estado ").append(enfocado.getEstadoTexto())
                            .append(": solo se recomiendan candidatos para proyectos activos o en espera.\n");
                } else if (enfocado == null && alcanceCandidatos.proyectosConVacantes() == 0) {
                    r.append("- No hay proyectos activos con vacantes en este momento. Para revisar candidatos de un proyecto")
                            .append(" con el equipo completo, nómbralo en tu consulta.\n");
                } else {
                    r.append("- No hay colaboradores disponibles con habilidades validadas para ")
                            .append(enfocado != null ? "este proyecto" : "las vacantes actuales").append(".\n");
                }
            } else {
                r.append("- He preseleccionado ").append(tarjetasCandidatos.size())
                        .append(" recomendación(es) priorizando habilidades validadas, disponibilidad, certificados y viabilidad presupuestal:\n");
                for (Map.Entry<String, CandidatoTarjetaRm> entry : tarjetasCandidatos.entrySet()) {
                    CandidatoTarjetaRm c = entry.getValue();
                    r.append("- [[CAND:").append(entry.getKey()).append("]] para ").append(c.proyectoNombre())
                            .append(" — ").append(c.matchPorcentaje()).append("% match | Cubre: ").append(c.habilidadesMatch())
                            .append(" | ").append(c.horasDisponibles()).append(" h/sem libres | Costo estimado: S/ ")
                            .append(c.costoTotalEstimado())
                            .append(c.cabeEnPresupuesto() ? " (dentro del presupuesto).\n" : " (excede presupuesto disponible).\n");
                }
                r.append("- Puedes proponer al candidato directamente al PM usando el botón **Proponer para el proyecto** en su tarjeta.\n");
                if (alcanceCandidatos.enfocadoCuposCompletos()) {
                    r.append("- El proyecto tiene sus cupos completos: al proponer deberás registrar una justificación de cupo.\n");
                }
            }
            if (enfocado == null && alcanceCandidatos.proyectosConVacantes() > alcanceCandidatos.proyectosAnalizados()) {
                r.append("- Analicé los ").append(alcanceCandidatos.proyectosAnalizados())
                        .append(" proyectos con vacantes de mayor prioridad (hay ").append(alcanceCandidatos.proyectosConVacantes())
                        .append("). Para otro proyecto, nómbralo en tu consulta.\n");
            }
            return r.toString();
        }

        if (pideHabilidades) {
            if (lineasBrechas.isEmpty()) {
                r.append("- Todos los cupos por habilidad requerida están cubiertos en los proyectos analizados.\n");
            } else {
                for (String b : lineasBrechas) {
                    r.append("- ").append(b).append('\n');
                }
            }
            return r.toString();
        }

        if (pidePresupuesto) {
            if (lineasPresupuesto.isEmpty()) {
                r.append("- No hay proyectos registrados para evaluar presupuesto.\n");
            } else {
                for (String p : lineasPresupuesto) {
                    r.append("- ").append(p).append('\n');
                }
            }
            return r.toString();
        }

        if (pideValidaciones) {
            r.append("- Certificados técnicos pendientes de revisión del RM: ").append(certsPend).append(".\n");
            r.append("- Formaciones académicas pendientes de revisión del RM: ").append(eduPend).append(".\n");
            r.append("- Recuerda que al aprobar certificados o habilidades, el colaborador pasa inmediatamente a ser elegible en Talent Matching.\n");
            return r.toString();
        }

        if (pidePendientes) {
            if (lineasPendientesRm.isEmpty() && certsPend == 0 && eduPend == 0) {
                r.append("- No tienes asignaciones, solicitudes de personal ni validaciones pendientes en este momento.\n");
            } else {
                for (String pend : lineasPendientesRm) {
                    r.append("- ").append(pend).append('\n');
                }
                if (certsPend > 0 || eduPend > 0) {
                    r.append("- Validaciones pendientes: ").append(certsPend).append(" certificado(s) y ")
                            .append(eduPend).append(" formación(es) académica(s).\n");
                }
            }
            return r.toString();
        }

        for (RmProyectoView v : analizados) {
            Proyecto p = v.getProyecto();
            r.append("- Proyecto [[PROY:").append(p.getId()).append("]]: estado ").append(v.getEstadoTexto())
                    .append(", equipo ").append(v.getIntegrantesActivos()).append('/').append(p.getColaboradoresRequeridos())
                    .append(" (").append(v.getVacantes()).append(" vacante(s)), pendientes RM: ").append(v.getPendientesRm()).append(".\n");
        }
        return r.toString();
    }

    private static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim();
    }

    private static boolean esDotable(Proyecto p) {
        return p.getEstado() == EstadoProyecto.ACTIVO || p.getEstado() == EstadoProyecto.EN_ESPERA;
    }

    private static int rangoPrioridad(Proyecto p) {
        if (p.getPrioridad() == null) return 3;
        return switch (p.getPrioridad()) {
            case ALTA -> 0;
            case MEDIA -> 1;
            case BAJA -> 2;
        };
    }

    // S17: texto escrito por usuarios (nombres de proyecto, PM, colaboradores) marcado como dato para la IA.
    private static String dato(String s) {
        return AsistenteSeguridadService.envolverDato(s);
    }

    private static String limpiar(String s) {
        return AsistenteSeguridadService.sanitizarDato(s);
    }
}
