package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoHabilidadRequeridaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RmProyectoConsultaService {
    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;
    private final RmPresupuestoService presupuestoService;

    public RmProyectoConsultaService(ProyectoRepository proyectoRepository,
                                     AsignacionRepository asignacionRepository,
                                     ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository,
                                     RmPresupuestoService presupuestoService) {
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.habilidadRequeridaRepository = habilidadRequeridaRepository;
        this.presupuestoService = presupuestoService;
    }

    @Transactional(readOnly = true)
    public List<RmProyectoView> listar() {
        return proyectoRepository.findAllConPmOrderByFechaCreacionDesc().stream()
                .map(this::crearVista)
                .toList();
    }

    @Transactional(readOnly = true)
    public RmProyectoView obtener(Long id) {
        Proyecto proyecto = proyectoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto solicitado."));
        proyecto.getPm().getNombre();
        return crearVista(proyecto);
    }

    private RmProyectoView crearVista(Proyecto proyecto) {
        List<Asignacion> asignaciones = asignacionRepository.findByProyecto(proyecto);
        List<Asignacion> activas = asignaciones.stream()
                .filter(a -> a.getEstado() == EstadoAsignacion.ACTIVA)
                .toList();

        int pendientesRm = (int) asignaciones.stream()
                .filter(this::requiereDecisionRm)
                .count();

        List<RmProyectoView.MiembroEquipo> equipo = activas.stream()
                .map(a -> crearMiembro(a.getColaborador(), a, proyecto))
                .toList();

        List<RmProyectoView.RequisitoTalento> requisitos = habilidadRequeridaRepository.findByProyecto(proyecto)
                .stream()
                .map(r -> new RmProyectoView.RequisitoTalento(
                        r.getHabilidad().getNombre(),
                        r.getNivelRequerido() == null ? "Sin definir" : textoEnum(r.getNivelRequerido().name()),
                        r.getCantidadPersonas()))
                .toList();

        int vacantes = Math.max(0, proyecto.getColaboradoresRequeridos() - activas.size());
        return new RmProyectoView(
                proyecto,
                nombreCompleto(proyecto.getPm()),
                textoEnum(proyecto.getEstado().name()),
                textoEnum(proyecto.getPrioridad().name()),
                activas.size(),
                vacantes,
                pendientesRm,
                equipo,
                requisitos,
                presupuestoService.calcularResumen(proyecto));
    }

    private boolean requiereDecisionRm(Asignacion asignacion) {
        if (asignacion.getEstado() != EstadoAsignacion.PENDIENTE || asignacion.isAprobadoPorRm()) return false;
        return asignacion.getOrigen() == OrigenAsignacion.PROPUESTA_PM
                || asignacion.getOrigen() == OrigenAsignacion.SOLICITADA_COLABORADOR;
    }

    private RmProyectoView.MiembroEquipo crearMiembro(Usuario usuario, Asignacion asignacion, Proyecto proyecto) {
        String nivel = usuario.getNivelExperiencia() == null
                ? "Sin definir"
                : (usuario.getNivelExperiencia().name().equals("SEMI_SENIOR")
                    ? "Semi Senior" : textoEnum(usuario.getNivelExperiencia().name()));
        RmPresupuestoService.CostoAsignacion costo =
                presupuestoService.calcularCosto(proyecto, usuario, asignacion.getHorasSemanales());
        return new RmProyectoView.MiembroEquipo(
                usuario.getId(),
                nombreCompleto(usuario),
                iniciales(usuario),
                valor(usuario.getCargo(), "Cargo sin registrar"),
                nivel,
                asignacion.getHorasSemanales(),
                costo.costoSemanal(),
                costo.costoTotal(),
                costo.calculable());
    }

    private String nombreCompleto(Usuario usuario) {
        String completo = (valor(usuario.getNombre(), "") + " " + valor(usuario.getApellido(), "")).trim();
        return completo.isEmpty() ? usuario.getCorreo() : completo;
    }

    private String iniciales(Usuario usuario) {
        String nombre = valor(usuario.getNombre(), "").trim();
        String apellido = valor(usuario.getApellido(), "").trim();
        String texto = (nombre.isEmpty() ? "" : nombre.substring(0, 1))
                + (apellido.isEmpty() ? "" : apellido.substring(0, 1));
        return texto.isEmpty() ? "CO" : texto.toUpperCase();
    }

    private String valor(String texto, String alternativa) {
        return texto == null || texto.isBlank() ? alternativa : texto;
    }

    private String textoEnum(String valor) {
        String texto = valor.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
