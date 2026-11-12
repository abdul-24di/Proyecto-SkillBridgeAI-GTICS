package com.pucp.skillb_ia.service.rm;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RmRevisionScheduler {

    private final RmResponsableService rmResponsableService;

    public RmRevisionScheduler(RmResponsableService rmResponsableService) {
        this.rmResponsableService = rmResponsableService;
    }

    // Ejecuta diariamente a las 8:00 a. m. la revisión de proyectos pendientes,
    // asignando un RM cuando sea necesario o enviando recordatorios al responsable.
    @Scheduled(cron = "0 0 * * * *")
    public void escalarProyectos() {
        rmResponsableService.escalarProyectosEnRevision();
    }

}