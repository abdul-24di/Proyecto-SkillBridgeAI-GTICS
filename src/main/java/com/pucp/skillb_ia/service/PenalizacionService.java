package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Penalizacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.TipoPenalizacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.PenalizacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Service
public class PenalizacionService {

    private static final int MAXIMO_STRIKES = 3;
    private static final BigDecimal MONTO_PLACEHOLDER = BigDecimal.ONE;

    private final PenalizacionRepository penalizacionRepository;
    private final AsignacionRepository asignacionRepository;
    private final AuditoriaService auditoriaService;

    public PenalizacionService(PenalizacionRepository penalizacionRepository,
                               AsignacionRepository asignacionRepository,
                               AuditoriaService auditoriaService) {
        this.penalizacionRepository = penalizacionRepository;
        this.asignacionRepository = asignacionRepository;
        this.auditoriaService = auditoriaService;
    }

    // ============================================================
    // APLICAMOS STRIKES
    // ============================================================

    @Transactional
    public void aplicarStrikePorTardanza(Actividad actividad) {
        if (penalizacionRepository.existsByActividad(actividad)) {
            return; // En caso de que ya tenga un strike por esa actividad, ya no se duplicará
        }
        registrarStrike(actividad, "Entregó la actividad \"" + actividad.getTitulo()
                + "\" fuera del plazo (vencía el " + actividad.getFechaLimite() + ").");
    }

    @Transactional
    public void aplicarStrikePorDevolucion(Actividad actividad) {
        registrarStrike(actividad, "El PM devolvió la actividad \"" + actividad.getTitulo()
                + "\" por no estar bien hecha.");
    }

    private void registrarStrike(Actividad actividad, String motivo) {
        Penalizacion strike = new Penalizacion();
        strike.setColaborador(actividad.getColaborador());
        strike.setProyecto(actividad.getProyecto());
        strike.setActividad(actividad);
        strike.setTipo(TipoPenalizacion.ACTIVIDAD_TARDIA);
        strike.setMotivo(motivo);
        strike.setMonto(MONTO_PLACEHOLDER);
        penalizacionRepository.save(strike);

        auditoriaService.registrar(actividad.getColaborador(), "APLICAR_STRIKE", "ACTIVIDAD", actividad.getId(), motivo);
    }

    // ============================================================
    // CONTAMOS Y REMOVEMOS AL TERCER STRIKE
    // ============================================================

    public int contarStrikes(Usuario colaborador, Proyecto proyecto) {
        List<Penalizacion> todas = penalizacionRepository.findByColaborador(colaborador);
        int contador = 0;

        for (Penalizacion p : todas) {
            boolean esDeEsteProyecto = p.getProyecto() != null && p.getProyecto().getId().equals(proyecto.getId());
            boolean esStrike = p.getTipo() == TipoPenalizacion.ACTIVIDAD_TARDIA;
            if (esDeEsteProyecto && esStrike) {
                contador++;
            }
        }
        return contador;
    }

    @Transactional
    public void verificarYRemoverPorStrikes(Usuario colaborador, Proyecto proyecto) {
        int strikes = contarStrikes(colaborador, proyecto);
        if (strikes < MAXIMO_STRIKES) {
            return;
        }

        List<Asignacion> asignacionesActivas = asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA);
        for (Asignacion asignacion : asignacionesActivas) {
            boolean esDeEsteProyecto = asignacion.getProyecto().getId().equals(proyecto.getId());
            if (!esDeEsteProyecto) {
                continue;
            }

            asignacion.setEstado(EstadoAsignacion.FINALIZADA);
            asignacion.setMotivoFinalizacion(MotivoFinalizacion.BAJO_DESEMPENO);
            asignacion.setFechaFinalizacion(LocalDateTime.now());
            asignacionRepository.save(asignacion);

            Penalizacion salida = new Penalizacion();
            salida.setColaborador(colaborador);
            salida.setProyecto(proyecto);
            salida.setTipo(TipoPenalizacion.SALIDA_PROYECTO);
            salida.setMotivo("Removido del proyecto \"" + proyecto.getNombre() + "\" por acumular " + strikes + " strikes.");
            salida.setMonto(MONTO_PLACEHOLDER);
            penalizacionRepository.save(salida);

            auditoriaService.registrar(colaborador, "REMOVER_POR_STRIKES", "ASIGNACION", asignacion.getId(),
                    "Removido del proyecto \"" + proyecto.getNombre() + "\" tras acumular " + strikes + " strikes.");
        }
    }

    @Transactional
    public void revisarVencidasSinEntregar(List<Actividad> actividades) {
        LocalDate hoy = LocalDate.now();

        for (Actividad actividad : actividades) {
            boolean sinEntregar = actividad.getEstado() == EstadoActividad.PENDIENTE
                    || actividad.getEstado() == EstadoActividad.EN_PROGRESO;
            boolean estaVencida = hoy.isAfter(actividad.getFechaLimite());

            if (sinEntregar && estaVencida) {
                aplicarStrikePorTardanza(actividad);
                verificarYRemoverPorStrikes(actividad.getColaborador(), actividad.getProyecto());
            }
        }
    }
}