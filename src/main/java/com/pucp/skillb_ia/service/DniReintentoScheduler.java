package com.pucp.skillb_ia.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tarea programada que vuelve a verificar los DNI pendientes. */
@Component
public class DniReintentoScheduler {

    private final DniVerificacionService dniVerificacionService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public DniReintentoScheduler(DniVerificacionService dniVerificacionService) {
        this.dniVerificacionService = dniVerificacionService;
    }

    /**
     * Reintenta la verificación de los DNI pendientes.
     * <p>
     * Cada hora (minuto 15) vuelve a consultar los DNI que quedaron "sin verificar".
     */
    @Scheduled(cron = "${app.dni.reintento-cron:0 15 * * * *}")
    public void reintentarDniSinVerificar() {
        dniVerificacionService.reintentarPendientes();
    }
}
