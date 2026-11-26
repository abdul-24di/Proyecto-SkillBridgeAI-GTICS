package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaborador;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.limpiarSesion;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.pm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.sesionComo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Qué ve cada RM: lo que tiene asignado y lo que todavía no tiene responsable; nunca lo asignado a otro RM.
// También cubre quién es el RM de la sesión (rmActual / idRmActual).
class RmAlcanceUnitTests {

    private final RmAlcanceService alcance = new RmAlcanceService();

    @AfterEach
    void limpiar() {
        limpiarSesion();
    }

    @Nested
    class RmActual {

        @Test
        void sinAutenticacionDevuelveNull() {
            assertNull(alcance.rmActual());
            assertNull(alcance.idRmActual());
        }

        @Test
        void principalQueNoEsUsuarioDetailsDevuelveNull() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("anonimo", null));
            assertNull(alcance.rmActual());
            assertNull(alcance.idRmActual());
        }

        @Test
        void pmEnSesionDevuelveNull() {
            sesionComo(pm(2L));
            assertNull(alcance.idRmActual());
        }

        @Test
        void colaboradorEnSesionDevuelveNull() {
            sesionComo(colaborador(3L));
            assertNull(alcance.idRmActual());
        }

        @Test
        void rmEnSesionDevuelveSuUsuarioYSuId() {
            Usuario yo = rm(1L);
            sesionComo(yo);
            assertSame(yo, alcance.rmActual());
            assertEquals(1L, alcance.idRmActual());
        }
    }

    @Nested
    class PuedeVer {

        @Test
        void elRmVeSusProyectosYLosQueNoTienenResponsablePeroNoLosDeOtroRm() {
            Usuario yo = rm(1L);
            Usuario otro = rm(2L);

            assertTrue(alcance.puedeVer(proyecto(yo), yo.getId()));
            assertTrue(alcance.puedeVer(proyecto(null), yo.getId()), "Sin responsable: cualquier RM puede tomarlo");
            assertFalse(alcance.puedeVer(proyecto(otro), yo.getId()), "Asignado a otro RM: no lo ve");
        }

        @Test
        void sinUnRmEnSesionNoSeFiltraNada() {
            assertTrue(alcance.puedeVer(proyecto(rm(2L)), null));
        }
    }

    @Nested
    class PuedeVerRevision {

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
            assertTrue(alcance.puedeVerRevision(rm(2L), null));
        }
    }

    private Proyecto proyecto(Usuario responsable) {
        Proyecto proyecto = new Proyecto();
        proyecto.setRmRevisor(responsable);
        return proyecto;
    }
}
