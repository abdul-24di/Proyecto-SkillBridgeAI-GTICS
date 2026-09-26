package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Penalizacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.TipoPenalizacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.PenalizacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;


@Service
public class PenalizacionService {

    private static final int MAXIMO_STRIKES = 3;
    private static final int STRIKES_POR_BLOQUE = 3;
    private static final int PORCENTAJE_POR_BLOQUE = 5;

    private final PenalizacionRepository penalizacionRepository;
    private final AsignacionRepository asignacionRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;

    public PenalizacionService(PenalizacionRepository penalizacionRepository,
                               AsignacionRepository asignacionRepository,
                               AuditoriaService auditoriaService,
                               NotificacionService notificacionService) {
        this.penalizacionRepository = penalizacionRepository;
        this.asignacionRepository = asignacionRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
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
        strike.setMonto(BigDecimal.ONE);
        penalizacionRepository.save(strike);

        auditoriaService.registrar(actividad.getColaborador(), "APLICAR_STRIKE", "ACTIVIDAD", actividad.getId(), motivo);

        int totalStrikes = contarStrikes(actividad.getColaborador(), actividad.getProyecto());
        notificacionService.crear(actividad.getColaborador(), "STRIKE",
                com.pucp.skillb_ia.model.enums.CategoriaNotificacion.ACTIVIDAD,
                "Strike en " + actividad.getProyecto().getNombre(),
                motivo + " Llevas " + totalStrikes + " de " + MAXIMO_STRIKES + " strikes en este proyecto.",
                "ACTIVIDAD", actividad.getId());
    }

    // ============================================================
    // CONTAMOS Y AVISAMOS AL PM Y COLABORADOR AL LLEGAR AL LÍMITE DE STRIKES
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
    public void verificarYNotificarPorStrikes(Usuario colaborador, Proyecto proyecto) {
        int strikes = contarStrikes(colaborador, proyecto);

        if (strikes != MAXIMO_STRIKES) {
            return;
        }

        //Buscamos la asignación activa para poder enlazar la notificación a ella.
        Long asignacionId = null;
        List<Asignacion> asignacionesActivas = asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA);
        for (Asignacion asignacion : asignacionesActivas) {
            if (asignacion.getProyecto().getId().equals(proyecto.getId())) {
                asignacionId = asignacion.getId();
                break;
            }
        }

        String nombreColaborador = colaborador.getNombre() + " " + colaborador.getApellido();

        notificacionService.crear(proyecto.getPm(), "STRIKES_LIMITE",
                com.pucp.skillb_ia.model.enums.CategoriaNotificacion.ASIGNACION,
                "Evaluar rendimiento en " + proyecto.getNombre(),
                nombreColaborador + " acumuló " + strikes + " strikes en \"" + proyecto.getNombre()
                        + "\". Evalúa su rendimiento y decide si continúa en el proyecto.",
                "ASIGNACION", asignacionId);

        notificacionService.crear(colaborador, "STRIKES_LIMITE",
                com.pucp.skillb_ia.model.enums.CategoriaNotificacion.ASIGNACION,
                "Alcanzaste el límite de strikes en " + proyecto.getNombre(),
                "Acumulaste " + strikes + " strikes por bajo desempeño en \"" + proyecto.getNombre()
                        + "\". Se te aplicará un descuento en tu sueldo, y el PM evaluará si continúas en el proyecto.",
                "ASIGNACION", asignacionId);

        auditoriaService.registrar(colaborador, "LIMITE_STRIKES_ALCANZADO", "ASIGNACION", asignacionId,
                nombreColaborador + " alcanzó " + strikes + " strikes en el proyecto \"" + proyecto.getNombre()
                        + "\" — se notificó al PM para que evalúe su rendimiento.");
    }

    @Transactional
    public void revisarVencidasSinEntregar(List<Actividad> actividades) {
        LocalDate hoy = LocalDate.now();

        for (Actividad actividad : actividades) {
            boolean sinEntregar = actividad.getEstado() == EstadoActividad.PENDIENTE || actividad.getEstado() == EstadoActividad.EN_PROGRESO;
            boolean estaVencida = hoy.isAfter(actividad.getFechaLimite());

            if (sinEntregar && estaVencida) {
                aplicarStrikePorTardanza(actividad);
                verificarYNotificarPorStrikes(actividad.getColaborador(), actividad.getProyecto());
            }
        }
    }

    // ============================================================
    // LISTADO DE DESCUENTOS PARA MOSTRAR AL COLABORADOR
    // ============================================================
    public List<Penalizacion> listarPenalizaciones(Usuario colaborador) {
        List<Penalizacion> todas = penalizacionRepository.findByColaborador(colaborador);

        //Ordenamos las penalizaciones de la fecha más reciente a la más antigua
        todas.sort( (a, b) -> b.getFecha().compareTo(a.getFecha()));

        return todas;
    }


    // ============================================================
    // DESCUENTO MENSUAL PARA MOSTRARLE AL COLABORADOR
    // ============================================================

    //Contamos los strikes totales del colaborador en todos sus proyectos en un mes
    public int contarStrikesDelMes(Usuario colaborador, YearMonth mes) {
        List<Penalizacion> todas = penalizacionRepository.findByColaborador(colaborador);
        int contador = 0;

        for (Penalizacion p : todas) {

            boolean esStrike = p.getTipo() == TipoPenalizacion.ACTIVIDAD_TARDIA;
            boolean esDeEseMes = p.getFecha() != null && YearMonth.from(p.getFecha()).equals(mes);

            if (esStrike && esDeEseMes) {
                contador++;
            }

        }
        return contador;
    }

    //Calculamos el porcentaje total a descontar por mes (5% por cada bloque completo de 3 strikes)
    public int calcularPorcentajeDescuentoMes(Usuario colaborador, YearMonth mes) {

        int strikesDelMes = contarStrikesDelMes(colaborador, mes);

        int bloquesCompletos = strikesDelMes / STRIKES_POR_BLOQUE;


        return bloquesCompletos * PORCENTAJE_POR_BLOQUE;
    }

    //Calculamos el monto en soles a descontar por mes, según el sueldo base del colaborador.
    public BigDecimal calcularMontoDescuentoMes(Usuario colaborador, java.time.YearMonth mes) {

        int porcentaje = calcularPorcentajeDescuentoMes(colaborador, mes);

        BigDecimal sueldoBase = colaborador.getSueldoBase();

        if (porcentaje == 0 || sueldoBase == null) {
            return BigDecimal.ZERO;
        }

        return sueldoBase.multiply(BigDecimal.valueOf(porcentaje)).divide(BigDecimal.valueOf(100));

    }

    //Revisamos si el colaborador tuvo algún descuento durante el año
    public boolean tuvoDescuentoEnElAnio(Usuario colaborador, int anio) {
        YearMonth ahora = YearMonth.now();

        for (int mes = 1; mes <= 12; mes++) {

            YearMonth ym = YearMonth.of(anio, mes);

            //no evaluamos meses que todavía no ocurren
            if (ym.isAfter(ahora)) {
                break;
            }
            //Si el porcentaje de descuento del mes es mayor a 0, el colaborador tuvo un descuento
            if (calcularPorcentajeDescuentoMes(colaborador, ym) > 0) {
                return true;
            }

        }

        return false;

    }







}