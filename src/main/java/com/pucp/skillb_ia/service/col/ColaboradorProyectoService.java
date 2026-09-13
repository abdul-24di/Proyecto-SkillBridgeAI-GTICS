package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ProyectoDisponibleView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequerida;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoHabilidadRequeridaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


@Service
public class ColaboradorProyectoService {

    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;
    private final AuditoriaService auditoriaService;

    public ColaboradorProyectoService(ProyectoRepository proyectoRepository,
                                      AsignacionRepository asignacionRepository,
                                      ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository,
                                      AuditoriaService auditoriaService) {
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.proyectoHabilidadRequeridaRepository = proyectoHabilidadRequeridaRepository;
        this.auditoriaService = auditoriaService;
    }

    // ============================================================
    // CONSULTA
    // ============================================================

    public List<ProyectoDisponibleView> listarProyectosDisponibles(Usuario colaborador) {
        List<Proyecto> proyectosActivos = proyectoRepository.findByEstado(EstadoProyecto.ACTIVO);
        List<ProyectoDisponibleView> disponibles = new ArrayList<>();

        for (Proyecto proyecto : proyectosActivos) {
            ProyectoDisponibleView vista = construirVista(proyecto, colaborador);
            if (vista.getCuposDisponibles() > 0) {
                disponibles.add(vista);
            }
        }
        return disponibles;
    }

    private ProyectoDisponibleView construirVista(Proyecto proyecto, Usuario colaborador) {
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
        List<String> habilidades = new ArrayList<>();
        for (ProyectoHabilidadRequerida requerida : requeridas) {
            habilidades.add(requerida.getHabilidad().getNombre());
        }

        int cupos = proyecto.getColaboradoresRequeridos() - activos;
        return new ProyectoDisponibleView(proyecto, activos, cupos, habilidades, yaTieneSolicitud);
    }

    // "El colaborador puede ver el estado de las solicitudes que ha enviado".
    public List<Asignacion> listarMisSolicitudes(Usuario colaborador) {
        List<Asignacion> todasMisAsignaciones = asignacionRepository.findByColaborador(colaborador);
        List<Asignacion> misSolicitudes = new ArrayList<>();

        for (Asignacion asignacion : todasMisAsignaciones) {
            if (asignacion.getOrigen() == OrigenAsignacion.SOLICITADA_COLABORADOR) {
                misSolicitudes.add(asignacion);
            }
        }

        // De la más reciente a la más antigua.
        misSolicitudes.sort(Comparator.comparing(Asignacion::getFechaSolicitud).reversed());
        return misSolicitudes;
    }

    // ============================================================
    // SOLICITAR INCORPORACIÓN
    // ============================================================

    @Transactional
    public void solicitarIncorporacion(Usuario colaborador, Long proyectoId, BigDecimal horasSemanales, String mensaje) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("El proyecto seleccionado no existe."));

        if (proyecto.getEstado() != EstadoProyecto.ACTIVO) {
            throw new IllegalArgumentException("Este proyecto ya no está activo.");
        }
        if (horasSemanales == null || horasSemanales.signum() <= 0) {
            throw new IllegalArgumentException("Indica cuántas horas semanales puedes dedicar.");
        }

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

        if (activos >= proyecto.getColaboradoresRequeridos()) {
            throw new IllegalArgumentException("Este proyecto ya no tiene cupos disponibles.");
        }
        if (yaTieneSolicitud) {
            throw new IllegalArgumentException("Ya tienes una solicitud pendiente o una asignación activa en este proyecto.");
        }

        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(colaborador);
        asignacion.setHorasSemanales(horasSemanales);
        asignacion.setOrigen(OrigenAsignacion.SOLICITADA_COLABORADOR);
        asignacion.setMensajeSolicitud(mensaje == null || mensaje.isBlank() ? null : mensaje.trim());
        // estado nace en PENDIENTE por el valor por defecto de la entidad;
        // A4 exige aprobación de PM y RM porque el origen es SOLICITADA_COLABORADOR.
        Asignacion guardada = asignacionRepository.save(asignacion);

        auditoriaService.registrar(colaborador, "SOLICITAR_ASIGNACION", "ASIGNACION", guardada.getId(),
                "Solicitó incorporarse al proyecto \"" + proyecto.getNombre() + "\".");
    }
}