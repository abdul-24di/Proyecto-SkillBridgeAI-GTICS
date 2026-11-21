package com.pucp.skillb_ia.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DniReintentoScheduler {

    private final DniVerificacionService dniVerificacionService;

    public DniReintentoScheduler(DniVerificacionService dniVerificacionService) {
        this.dniVerificacionService = dniVerificacionService;
    }

    // Cada hora (minuto 15) vuelve a consultar los DNI que quedaron "sin verificar".
    @Scheduled(cron = "${app.dni.reintento-cron:0 15 * * * *}")
    public void reintentarDniSinVerificar() {
        dniVerificacionService.reintentarPendientes();
    }
}
