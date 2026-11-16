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
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.HabilidadFaltante;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.RecomendacionCandidato;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

@Service
public class RmAsistenteContextoService {

    public record ContextoRm(
            String textoPrompt,
            String resumenAnaliticoLocal,
            RmProyectoView proyectoEnfocado,
            KpisAsistenteRm kpis,
            Map<Long, ProyectoTarjetaRm> proyectos,
            Map<String, CandidatoTarjetaRm> candidatos) {}

    private final RmProyectoConsultaService rmProyectoConsultaService;
    private final RmTalentMatchingService rmTalentMatchingService;
    private final RmAsignacionService rmAsignacionService;
    private final RmSolicitudPersonalService rmSolicitudPersonalService;
    private final RmCertificadoService rmCertificadoService;
    private final RmEducacionService rmEducacionService;

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

    @Transactional(readOnly = true)
    public KpisAsistenteRm obtenerKpis() {
        List<RmProyectoView> proyectos = rmProyectoConsultaService.listar();
        int vacantes = proyectos.stream()
                .filter(RmProyectoView::isConVacantesParaDotacion)
                .mapToInt(RmProyectoView::getVacantes)
                .sum();
        long asigPendientes = rmAsignacionService.listar().stream()
                .filter(RmAsignacionView::isRequiereDecisionRm)
                .count();
        long validacionesPendientes = rmCertificadoService.listarPendientes().size()
                + rmEducacionService.listarPendientes().size();
        return new KpisAsistenteRm(proyectos.size(), vacantes, asigPendientes, validacionesPendientes);
    }

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

    @Transactional(readOnly = true)
    public ContextoRm construir(Long proyectoContextoId, String mensaje) {
        String mensajeN = normalizar(mensaje);
        List<RmProyectoView> todos = rmProyectoConsultaService.listar();
        KpisAsistenteRm kpis = obtenerKpis();

        RmProyectoView enfocado = resolverProyectoEnfocado(todos, proyectoContextoId, mensajeN);
        List<RmProyectoView> analizados = enfocado != null ? List.of(enfocado) : todos;

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

            t.append("\n### Proyecto [[PROY:").append(p.getId()).append("]] \"").append(limpiar(p.getNombre())).append("\"\n");
            t.append("- PM: ").append(limpiar(v.getPmNombre()))
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

            List<HabilidadFaltante> faltantes = rmTalentMatchingService.calcularHabilidadesFaltantes(p);
            if (!faltantes.isEmpty()) {
                t.append("- Habilidades faltantes en el proyecto:\n");
                for (HabilidadFaltante hf : faltantes) {
                    String detalleBrecha = p.getNombre() + ": falta(n) " + hf.faltantes() + " cupo(s) de "
                            + hf.nombre() + " (" + hf.nivelRequerido() + ").";
                    lineasBrechas.add(detalleBrecha);
                    t.append("  * ").append(limpiar(detalleBrecha)).append('\n');
                }
            }

            if (p.getEstado() == EstadoProyecto.ACTIVO || p.getEstado() == EstadoProyecto.EN_ESPERA) {
                List<RecomendacionCandidato> recs = rmTalentMatchingService.recomendarParaProyecto(p, faltantes);
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
                            .append(limpiar(rc.nombre())).append(" | Match: ").append(rc.matchPorcentaje()).append("%")
                            .append(" | Cubre: ").append(limpiar(candRm.habilidadesMatch()))
                            .append(" | Disponibilidad: ").append(rc.horasDisponibles()).append(" h/sem")
                            .append(" | Costo estimado: S/ ").append(rc.costoTotalEstimado())
                            .append(" (cabe en presupuesto: ").append(rc.cabeEnPresupuesto() ? "sí" : "no").append(")\n");
                }
            }
        }

        // Asignaciones y solicitudes pendientes del RM
        t.append("\n## BANDEJA DE PENDIENTES DEL RM\n");
        for (RmAsignacionView asig : rmAsignacionService.listar()) {
            if (!asig.isRequiereDecisionRm()) continue;
            if (enfocado != null && !asig.getAsignacion().getProyecto().getId().equals(enfocado.getProyecto().getId())) {
                continue;
            }
            String linea = "Asignación #" + asig.getAsignacion().getId() + " en "
                    + asig.getAsignacion().getProyecto().getNombre() + ": " + asig.getColaboradorNombre()
                    + " (" + asig.getAsignacion().getHorasSemanales() + " h/sem, " + asig.getEstadoBandeja() + ").";
            lineasPendientesRm.add(linea);
            t.append("- ").append(limpiar(linea)).append('\n');
        }

        var solicitudes = rmSolicitudPersonalService.listarPagina(
                rmSolicitudPersonalService.normalizarFiltros(null, "PENDIENTE", null), "1").filas();
        for (RmSolicitudPersonalView sol : solicitudes) {
            if (enfocado != null && !sol.getProyecto().getProyecto().getId().equals(enfocado.getProyecto().getId())) {
                continue;
            }
            String linea = "Solicitud de personal " + sol.getCodigo() + " de " + sol.getPmNombre()
                    + " para " + sol.getSolicitud().getProyecto().getNombre() + ": solicita "
                    + sol.getSolicitud().getCantidadColaboradores() + " colaborador(es).";
            lineasPendientesRm.add(linea);
            t.append("- ").append(limpiar(linea)).append('\n');
        }

        int certsPend = rmCertificadoService.listarPendientes().size();
        int eduPend = rmEducacionService.listarPendientes().size();
        t.append("- Certificados pendientes de validación del RM: ").append(certsPend).append('\n');
        t.append("- Formaciones académicas pendientes de validación del RM: ").append(eduPend).append('\n');

        String resumenLocal = construirRespuestaAnaliticaLocal(
                mensajeN, enfocado, kpis, analizados, tarjetasProyectos,
                lineasPresupuesto, lineasBrechas, lineasPendientesRm, tarjetasCandidatos, certsPend, eduPend);

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
            int eduPend) {

        StringBuilder r = new StringBuilder();
        String alcance = enfocado != null
                ? "del proyecto **" + enfocado.getProyecto().getNombre() + "** [[PROY:" + enfocado.getProyecto().getId() + "]]"
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
                r.append("- No se encontraron candidatos disponibles con habilidades validadas que coincidan con las vacantes actuales.\n");
            } else {
                r.append("- He preseleccionado ").append(tarjetasCandidatos.size())
                        .append(" recomendación(es) priorizando habilidades validadas, disponibilidad, certificados y viabilidad presupuestal:\n");
                for (Map.Entry<String, CandidatoTarjetaRm> entry : tarjetasCandidatos.entrySet()) {
                    CandidatoTarjetaRm c = entry.getValue();
                    r.append("- [[CAND:").append(entry.getKey()).append("]] para ").append(c.proyectoNombre())
                            .append(" — ").append(c.matchPorcentaje()).append("% match | Cubre: ").append(c.habilidadesMatch())
                            .append(" | ").append(c.horasDisponibles()).append(" h/sem libres | Costo estimado: S/ ")
                            .append(c.costoTotalEstimado())
                            .append(c.cabeEnPresupuesto() ? " (dentro del presupuesto)." : " (excede presupuesto disponible).\n");
                }
                r.append("- Puedes proponer al candidato directamente al PM usando el botón **Proponer para el proyecto** en su tarjeta.\n");
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

    private static String limpiar(String s) {
        if (s == null) return "";
        return s.replace("[[", "(").replace("]]", ")");
    }
}
