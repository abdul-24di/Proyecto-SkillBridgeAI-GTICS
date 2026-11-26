package com.pucp.skillb_ia.service.rm;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada que escala los proyectos que siguen en revisión: asigna un RM a los que no tienen
 * responsable y recuerda la revisión a los que sí lo tienen. Delega en {@link RmResponsableService}.
 */
@Component
public class RmRevisionScheduler {

    private final RmResponsableService rmResponsableService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public RmRevisionScheduler(RmResponsableService rmResponsableService) {
        this.rmResponsableService = rmResponsableService;
    }

    /**
     * Ejecuta {@link RmResponsableService#escalarProyectosEnRevision()} al inicio de cada hora
     * (cron {@code 0 0 * * * *}).
     */
    @Scheduled(cron = "0 0 * * * *")
    public void escalarProyectos() {
        rmResponsableService.escalarProyectosEnRevision();
    }

}