package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RmTalentMatchingService {

    private static final String CLAVE_MAX_ASIGNACIONES = "MAX_ASIGNACIONES_POR_COLABORADOR";
    private static final int MAX_ASIGNACIONES_POR_DEFECTO = 3;
    private static final int TOP_RECOMENDACIONES = 6;

    public record HabilidadFaltante(
            Long habilidadId,
            String nombre,
            String nivelRequerido,
            int requeridos,
            int cubiertos,
            int faltantes,
            BigDecimal horasSemanales) implements Serializable {}

    public record RecomendacionCandidato(
            Long colaboradorId,
            String nombre,
            String iniciales,
            String cargo,
            String nivel,
            int matchPorcentaje,
            String matchClase,
            BigDecimal horasDisponibles,
            int asignacionesActivas,
            int maxAsignaciones,
            int certificadosAprobados,
            List<String> habilidadesValidadas,
            List<String> habilidadesQueCubre,
            BigDecimal horasSugeridas,
            BigDecimal costoTotalEstimado,
            boolean costoCalculable,
            boolean cabeEnPresupuesto,
            String explicacionIa) implements Serializable {}

    public record ResultadoMatching(
            List<RmProyectoView> proyectosConVacantes,
            RmProyectoView proyectoSeleccionado,
            List<HabilidadFaltante> habilidadesFaltantes,
            List<RecomendacionCandidato> recomendaciones) implements Serializable {}

    private final RmProyectoConsultaService rmProyectoConsultaService;
    private final ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;
    private final AsignacionRepository asignacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    private final CertificadoRepository certificadoRepository;
    private final ConfiguracionSistemaRepository configuracionSistemaRepository;
    private final RmPresupuestoService rmPresupuestoService;

    public RmTalentMatchingService(RmProyectoConsultaService rmProyectoConsultaService,
                                   ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository,
                                   AsignacionRepository asignacionRepository,
                                   UsuarioRepository usuarioRepository,
                                   ColaboradorHabilidadRepository colaboradorHabilidadRepository,
                                   CertificadoRepository certificadoRepository,
                                   ConfiguracionSistemaRepository configuracionSistemaRepository,
                                   RmPresupuestoService rmPresupuestoService) {
        this.rmProyectoConsultaService = rmProyectoConsultaService;
        this.habilidadRequeridaRepository = habilidadRequeridaRepository;
        this.asignacionRepository = asignacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.certificadoRepository = certificadoRepository;
        this.configuracionSistemaRepository = configuracionSistemaRepository;
        this.rmPresupuestoService = rmPresupuestoService;
    }

    @Transactional(readOnly = true)
    public ResultadoMatching construirMatching(RmProyectoView proyectoContexto) {
        List<RmProyectoView> todos = rmProyectoConsultaService.listar();
        List<RmProyectoView> conVacantes = todos.stream()
                .filter(p -> (p.getProyecto().getEstado() == EstadoProyecto.ACTIVO
                        || p.getProyecto().getEstado() == EstadoProyecto.EN_ESPERA)
                        && p.getVacantes() > 0)
                .toList();

        if (conVacantes.isEmpty()) {
            conVacantes = todos.stream()
                    .filter(p -> p.getProyecto().getEstado() == EstadoProyecto.ACTIVO
                            || p.getProyecto().getEstado() == EstadoProyecto.EN_ESPERA)
                    .toList();
        }

        RmProyectoView seleccionado = proyectoContexto;
        if (seleccionado == null && !conVacantes.isEmpty()) {
            seleccionado = conVacantes.get(0);
        } else if (seleccionado != null) {
            Long idSel = seleccionado.getProyecto().getId();
            boolean estaEnLista = conVacantes.stream().anyMatch(p -> p.getProyecto().getId().equals(idSel));
            if (!estaEnLista && (seleccionado.getProyecto().getEstado() == EstadoProyecto.ACTIVO
                    || seleccionado.getProyecto().getEstado() == EstadoProyecto.EN_ESPERA)) {
                List<RmProyectoView> ampliada = new ArrayList<>();
                ampliada.add(seleccionado);
                ampliada.addAll(conVacantes);
                conVacantes = List.copyOf(ampliada);
            }
        }

        if (seleccionado == null) {
            return new ResultadoMatching(conVacantes, null, List.of(), List.of());
        }

        List<HabilidadFaltante> faltantes = calcularHabilidadesFaltantes(seleccionado.getProyecto());
        List<RecomendacionCandidato> recomendaciones = recomendarParaProyecto(seleccionado.getProyecto(), faltantes);
        return new ResultadoMatching(conVacantes, seleccionado, faltantes, recomendaciones);
    }

    @Transactional(readOnly = true)
    public List<HabilidadFaltante> calcularHabilidadesFaltantes(Proyecto proyecto) {
        List<ProyectoHabilidadRequerida> requisitos = habilidadRequeridaRepository.findByProyecto(proyecto);
        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);

        Map<Long, Integer> cubiertosPorHabilidad = new HashMap<>();
        for (Asignacion a : activas) {
            if (a.getHabilidadSolicitada() != null) {
                cubiertosPorHabilidad.merge(a.getHabilidadSolicitada().getId(), 1, Integer::sum);
            } else {
                List<ColaboradorHabilidad> validadas = colaboradorHabilidadRepository
                        .findByColaboradorAndActivoTrueAndEstadoValidacion(a.getColaborador(), EstadoValidacion.VALIDADA);
                for (ColaboradorHabilidad ch : validadas) {
                    cubiertosPorHabilidad.merge(ch.getHabilidad().getId(), 1, Integer::sum);
                }
            }
        }

        List<HabilidadFaltante> resultado = new ArrayList<>();
        for (ProyectoHabilidadRequerida req : requisitos) {
            int exigidos = Math.max(1, req.getCantidadPersonas());
            int cubiertos = Math.min(exigidos, cubiertosPorHabilidad.getOrDefault(req.getHabilidad().getId(), 0));
            int faltan = Math.max(0, exigidos - cubiertos);
            if (faltan > 0) {
                resultado.add(new HabilidadFaltante(
                        req.getHabilidad().getId(),
                        req.getHabilidad().getNombre(),
                        textoNivelDominio(req.getNivelRequerido()),
                        exigidos,
                        cubiertos,
                        faltan,
                        req.getHorasSemanales() != null ? req.getHorasSemanales() : proyecto.getHorasSemanalesRequeridas()
                ));
            }
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<RecomendacionCandidato> recomendarParaProyecto(Proyecto proyecto, List<HabilidadFaltante> faltantes) {
        if (proyecto.getEstado() != EstadoProyecto.ACTIVO && proyecto.getEstado() != EstadoProyecto.EN_ESPERA) {
            return List.of();
        }
        int maxAsignaciones = obtenerMaxAsignaciones();
        List<ProyectoHabilidadRequerida> todosRequisitos = habilidadRequeridaRepository.findByProyecto(proyecto);
        Map<Long, ProyectoHabilidadRequerida> mapaRequisitos = todosRequisitos.stream()
                .collect(Collectors.toMap(r -> r.getHabilidad().getId(), r -> r, (a, b) -> a));
        Set<Long> idsFaltantes = faltantes.stream().map(HabilidadFaltante::habilidadId).collect(Collectors.toSet());

        Set<Long> yaEnProyecto = asignacionRepository.findByProyecto(proyecto).stream()
                .filter(a -> a.getEstado() == EstadoAsignacion.ACTIVA || a.getEstado() == EstadoAsignacion.PENDIENTE)
                .map(a -> a.getColaborador().getId())
                .collect(Collectors.toSet());

        BigDecimal horasBaseProyecto = proyecto.getHorasSemanalesRequeridas() != null
                && proyecto.getHorasSemanalesRequeridas().signum() > 0
                ? proyecto.getHorasSemanalesRequeridas()
                : new BigDecimal("16.00");

        List<RecomendacionCandidato> candidatos = new ArrayList<>();
        for (Usuario col : usuarioRepository.findColaboradoresVisiblesRm()) {
            if (yaEnProyecto.contains(col.getId())) {
                continue;
            }
            BigDecimal disp = col.getHorasDisponibles() != null ? col.getHorasDisponibles() : BigDecimal.ZERO;
            if (disp.compareTo(BigDecimal.ONE) < 0) {
                continue;
            }

            List<ColaboradorHabilidad> validadas = colaboradorHabilidadRepository
                    .findByColaboradorAndActivoTrueAndEstadoValidacion(col, EstadoValidacion.VALIDADA);
            if (validadas.isEmpty()) {
                continue;
            }

            int activas = (int) asignacionRepository.countByColaboradorAndEstado(col, EstadoAsignacion.ACTIVA);
            int certsAprobados = (int) certificadoRepository.findByColaborador(col).stream()
                    .filter(c -> c.getEstado() == EstadoCertificado.APROBADO)
                    .count();

            List<String> todasNombres = new ArrayList<>();
            List<String> cubreNombres = new ArrayList<>();
            int puntajeSkills = 0;

            for (ColaboradorHabilidad ch : validadas) {
                Long habId = ch.getHabilidad().getId();
                String nombreHab = ch.getHabilidad().getNombre();
                todasNombres.add(nombreHab + " (" + textoCortoDominio(ch.getNivelDominio()) + ")");

                if (idsFaltantes.contains(habId) || mapaRequisitos.containsKey(habId)) {
                    ProyectoHabilidadRequerida req = mapaRequisitos.get(habId);
                    boolean cumpleNivel = req == null || req.getNivelRequerido() == null
                            || ch.getNivelDominio().compareTo(req.getNivelRequerido()) >= 0;
                    cubreNombres.add(nombreHab);
                    puntajeSkills += idsFaltantes.contains(habId)
                            ? (cumpleNivel ? 42 : 26)
                            : (cumpleNivel ? 28 : 18);
                }
            }

            if (cubreNombres.isEmpty() && !mapaRequisitos.isEmpty()) {
                continue;
            }
            if (cubreNombres.isEmpty()) {
                puntajeSkills = 35;
            }

            // Puntaje por disponibilidad horaria (hasta 22 pts)
            int puntajeDisponibilidad = disp.compareTo(horasBaseProyecto) >= 0 ? 22
                    : (disp.compareTo(new BigDecimal("8")) >= 0 ? 14 : 7);

            // Puntaje por carga de asignaciones (hasta 15 pts)
            int puntajeCarga = activas == 0 ? 15 : (activas < maxAsignaciones ? 10 : 2);

            // Puntaje por nivel de experiencia y certificados aprobados (hasta 18 pts)
            int puntajeExperiencia = switch (col.getNivelExperiencia() != null ? col.getNivelExperiencia() : NivelExperiencia.JUNIOR) {
                case SENIOR -> 12;
                case SEMI_SENIOR -> 8;
                case JUNIOR -> 5;
            } + Math.min(6, certsAprobados * 2);

            int match = Math.min(98, Math.max(45, puntajeSkills + puntajeDisponibilidad + puntajeCarga + puntajeExperiencia));
            String matchClase = match >= 85 ? "bg-green-lt text-green"
                    : (match >= 70 ? "bg-yellow-lt text-yellow" : "bg-secondary-lt");

            BigDecimal horasSugeridas = disp.min(horasBaseProyecto).max(BigDecimal.ONE);
            RmPresupuestoService.ImpactoPresupuesto impacto = rmPresupuestoService.calcularImpacto(
                    proyecto, col, horasSugeridas);
            BigDecimal costoTotal = impacto.costo() != null && impacto.costo().calculable()
                    ? impacto.costo().costoTotal() : BigDecimal.ZERO;
            boolean costoCalculable = impacto.costo() != null && impacto.costo().calculable();
            boolean cabeEnPresupuesto = impacto.suficiente();

            String explicacion = construirExplicacionIa(
                    cubreNombres, disp, activas, maxAsignaciones, textoNivelExperiencia(col.getNivelExperiencia()),
                    certsAprobados, costoCalculable, costoTotal, cabeEnPresupuesto);

            candidatos.add(new RecomendacionCandidato(
                    col.getId(),
                    col.getNombre() + " " + col.getApellido(),
                    iniciales(col.getNombre(), col.getApellido()),
                    col.getCargo() != null ? col.getCargo().getNombre() : "Colaborador",
                    textoNivelExperiencia(col.getNivelExperiencia()),
                    match,
                    matchClase,
                    disp,
                    activas,
                    maxAsignaciones,
                    certsAprobados,
                    todasNombres.subList(0, Math.min(5, todasNombres.size())),
                    cubreNombres,
                    horasSugeridas,
                    costoTotal,
                    costoCalculable,
                    cabeEnPresupuesto,
                    explicacion
            ));
        }

        candidatos.sort(Comparator.comparingInt(RecomendacionCandidato::matchPorcentaje).reversed()
                .thenComparing(RecomendacionCandidato::cabeEnPresupuesto, Comparator.reverseOrder())
                .thenComparing(RecomendacionCandidato::horasDisponibles, Comparator.reverseOrder()));

        return candidatos.subList(0, Math.min(TOP_RECOMENDACIONES, candidatos.size()));
    }

    private String construirExplicacionIa(List<String> cubre, BigDecimal disp, int activas, int maxAsig,
                                          String nivel, int certs, boolean calculable, BigDecimal costo,
                                          boolean cabePresupuesto) {
        StringBuilder sb = new StringBuilder();
        if (!cubre.isEmpty()) {
            sb.append("Cubre las habilidades requeridas (").append(String.join(", ", cubre)).append(")");
        } else {
            sb.append("Cuenta con habilidades validadas por el RM");
        }
        sb.append(", nivel ").append(nivel).append(" con ").append(certs).append(" certificado(s) aprobado(s). ");
        sb.append("Dispone de ").append(disp).append(" h/sem y carga actual de ").append(activas)
                .append("/").append(maxAsig).append(" asignaciones. ");
        if (calculable) {
            sb.append("Costo estimado para el proyecto: S/ ").append(costo)
                    .append(cabePresupuesto ? " (dentro del presupuesto disponible)." : " (supera el saldo disponible del proyecto).");
        }
        return sb.toString();
    }

    private int obtenerMaxAsignaciones() {
        return configuracionSistemaRepository.findByClave(CLAVE_MAX_ASIGNACIONES)
                .map(ConfiguracionSistema::getValor)
                .map(valor -> {
                    try {
                        return Integer.parseInt(valor.trim());
                    } catch (NumberFormatException ex) {
                        return MAX_ASIGNACIONES_POR_DEFECTO;
                    }
                })
                .orElse(MAX_ASIGNACIONES_POR_DEFECTO);
    }

    private static String textoNivelDominio(NivelDominio nivel) {
        if (nivel == null) return "Cualquier nivel";
        return switch (nivel) {
            case BASICO -> "Nivel básico";
            case INTERMEDIO -> "Nivel intermedio";
            case AVANZADO -> "Nivel avanzado";
        };
    }

    private static String textoCortoDominio(NivelDominio nivel) {
        if (nivel == null) return "Validada";
        return switch (nivel) {
            case BASICO -> "Básico";
            case INTERMEDIO -> "Intermedio";
            case AVANZADO -> "Avanzado";
        };
    }

    private static String textoNivelExperiencia(NivelExperiencia nivel) {
        if (nivel == null) return "Junior";
        return switch (nivel) {
            case JUNIOR -> "Junior";
            case SEMI_SENIOR -> "Semi Senior";
            case SENIOR -> "Senior";
        };
    }

    private static String iniciales(String nombre, String apellido) {
        String n = (nombre != null && !nombre.isEmpty()) ? String.valueOf(nombre.charAt(0)) : "";
        String a = (apellido != null && !apellido.isEmpty()) ? String.valueOf(apellido.charAt(0)) : "";
        return (n + a).toUpperCase(Locale.ROOT);
    }
}
