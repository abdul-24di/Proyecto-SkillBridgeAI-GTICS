package com.pucp.skillb_ia.service.rm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

// El programador solo delega el escalamiento en RmResponsableService.
@ExtendWith(MockitoExtension.class)
class RmRevisionSchedulerUnitTests {

    @Mock
    private RmResponsableService rmResponsableService;

    @InjectMocks
    private RmRevisionScheduler scheduler;

    @Test
    void escalarProyectosDelegaEnElServicioDeResponsable() {
        scheduler.escalarProyectos();

        verify(rmResponsableService).escalarProyectosEnRevision();
        verifyNoMoreInteractions(rmResponsableService);
    }
}
