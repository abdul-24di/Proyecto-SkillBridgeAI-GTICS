package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.service.rm.RmAlcanceService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Qué ve cada RM: lo que tiene asignado y lo que todavía no tiene responsable; nunca lo asignado a otro RM.
class RmAlcanceTests {

    private final RmAlcanceService alcance = new RmAlcanceService();

    @Test
    void elRmVeSusProyectosYLosQueNoTienenResponsablePeroNoLosDeOtroRm() {
        Usuario yo = rm(1L);
        Usuario otro = rm(2L);

        assertTrue(alcance.puedeVer(proyecto(yo), yo.getId()));
        assertTrue(alcance.puedeVer(proyecto(null), yo.getId()), "Sin responsable: cualquier RM puede tomarlo");
        assertFalse(alcance.puedeVer(proyecto(otro), yo.getId()), "Asignado a otro RM: no lo ve");
    }

    @Test
    void elRmRevisaLoQueSeLeAsignoYLoQueNoTieneResponsablePeroNoLoDeOtroRm() {
        Usuario yo = rm(1L);
        Usuario otro = rm(2L);

        assertTrue(alcance.puedeVerRevision(yo, yo.getId()));
        assertTrue(alcance.puedeVerRevision(null, yo.getId()), "Sin responsable: no debe quedar sin revisión");
        assertFalse(alcance.puedeVerRevision(otro, yo.getId()), "Asignado a otro RM: no lo revisa");
    }

    @Test
    void sinUnRmEnSesionNoSeFiltraNada() {
        assertTrue(alcance.puedeVer(proyecto(rm(2L)), null));
        assertTrue(alcance.puedeVerRevision(rm(2L), null));
    }

    private Usuario rm(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    private Proyecto proyecto(Usuario responsable) {
        Proyecto proyecto = new Proyecto();
        proyecto.setRmRevisor(responsable);
        return proyecto;
    }
}
