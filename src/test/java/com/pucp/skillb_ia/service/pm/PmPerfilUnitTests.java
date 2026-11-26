package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmPerfilView;
import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de {@link PmPerfilService} con JUnit 5 y Mockito (sin Spring ni BD). */
@ExtendWith(MockitoExtension.class)
class PmPerfilUnitTests {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private ArchivoAlmacenamientoService archivoAlmacenamientoService;

    @InjectMocks
    private PmPerfilService servicio;

    @Test
    void obtenerConstruyeVistaConDatosDelPm() {
        Usuario pm = PmDatosPrueba.pm(1L);
        pm.setNombre("Ana");
        pm.setApellido("Torres");

        PmPerfilView vista = servicio.obtener(pm);

        assertEquals("Ana Torres", vista.getNombreCompleto());
        assertEquals("AT", vista.getIniciales());
    }

    @Nested
    class ActualizarDatos {

        @Test
        void actualizaCamposLimpiandoEspaciosSinModificarCargo() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Cargo cargoActual = new Cargo();
            cargoActual.setNombre("Project Manager Senior");
            pm.setCargo(cargoActual);

            servicio.actualizarDatos("  Laura  ", "  Gómez  ", "Otro Cargo", "  987654321  ", pm);

            assertEquals("Laura", pm.getNombre());
            assertEquals("Gómez", pm.getApellido());
            assertEquals("987654321", pm.getTelefono());
            assertEquals("Project Manager Senior", pm.getCargo().getNombre());
            verify(usuarioRepository).save(pm);
            verify(auditoriaService).registrar(eq(pm), eq("ACTUALIZAR_PERFIL"), eq("USUARIO"), eq(1L), anyString());
        }

        @Test
        void conservaApellidoSiVieneNullYDejaTelefonoNull() {
            Usuario pm = PmDatosPrueba.pm(1L);
            pm.setApellido("Quispe");

            servicio.actualizarDatos("Carlos", null, null, null, pm);

            assertEquals("Quispe", pm.getApellido());
            assertNull(pm.getTelefono());
            verify(usuarioRepository).save(pm);
        }

        @Test
        void rechazaNombreVacio() {
            Usuario pm = PmDatosPrueba.pm(1L);

            assertThrows(IllegalArgumentException.class,
                    () -> servicio.actualizarDatos("   ", "Pérez", null, null, pm));

            verify(usuarioRepository, never()).save(any());
        }
    }

    @Nested
    class ActualizarFoto {

        @Test
        void rechazaArchivoNuloOVacio() {
            Usuario pm = PmDatosPrueba.pm(1L);
            MockMultipartFile vacio = new MockMultipartFile("foto", "a.png", "image/png", new byte[0]);

            assertThrows(IllegalArgumentException.class, () -> servicio.actualizarFoto(null, pm));
            assertThrows(IllegalArgumentException.class, () -> servicio.actualizarFoto(vacio, pm));
        }

        @Test
        void rechazaTipoNoPermitido() {
            Usuario pm = PmDatosPrueba.pm(1L);
            MockMultipartFile pdf = new MockMultipartFile("foto", "a.pdf", "application/pdf", new byte[]{1, 2});

            assertThrows(IllegalArgumentException.class, () -> servicio.actualizarFoto(pdf, pm));
            verify(archivoAlmacenamientoService, never()).guardar(any(), anyString(), anyString());
        }

        @Test
        void rechazaArchivoQueSuperaDosMb() {
            Usuario pm = PmDatosPrueba.pm(1L);
            MockMultipartFile grande = new MockMultipartFile(
                    "foto", "grande.jpg", "image/jpeg", new byte[(2 * 1024 * 1024) + 1]);

            assertThrows(IllegalArgumentException.class, () -> servicio.actualizarFoto(grande, pm));
        }

        @Test
        void guardaFotoValidaYRegistraAuditoria() {
            Usuario pm = PmDatosPrueba.pm(5L);
            MockMultipartFile png = new MockMultipartFile("foto", "avatar.png", "image/png", new byte[]{1, 2, 3});
            when(archivoAlmacenamientoService.guardar(eq(png), eq("perfil"), contains(".png")))
                    .thenReturn("/uploads/perfil/usuario-5.png");

            servicio.actualizarFoto(png, pm);

            assertEquals("/uploads/perfil/usuario-5.png", pm.getFotoUrl());
            verify(usuarioRepository).save(pm);
            verify(auditoriaService).registrar(eq(pm), eq("ACTUALIZAR_PERFIL"), eq("USUARIO"), eq(5L), anyString());
        }
    }

    @Nested
    class ActualizarPassword {

        @Test
        void rechazaPasswordActualIncorrecta() {
            Usuario pm = PmDatosPrueba.pm(1L);
            when(passwordEncoder.matches("mala", "hash-actual")).thenReturn(false);

            assertThrows(IllegalArgumentException.class,
                    () -> servicio.actualizarPassword("mala", "Segura1!", pm));

            verify(usuarioRepository, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"corta1!", "sinmayuscula1!", "SinNumero!", "SinSimbolo1"})
        void rechazaPasswordNuevaDebil(String debil) {
            Usuario pm = PmDatosPrueba.pm(1L);
            when(passwordEncoder.matches("actual", "hash-actual")).thenReturn(true);

            assertThrows(IllegalArgumentException.class,
                    () -> servicio.actualizarPassword("actual", debil, pm));

            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void guardaNuevoHashYRegistraAuditoria() {
            Usuario pm = PmDatosPrueba.pm(1L);
            when(passwordEncoder.matches("actual", "hash-actual")).thenReturn(true);
            when(passwordEncoder.encode("NuevaSegura1!")).thenReturn("hash-nuevo");

            servicio.actualizarPassword("actual", "NuevaSegura1!", pm);

            assertEquals("hash-nuevo", pm.getPasswordHash());
            verify(usuarioRepository).save(pm);
            verify(auditoriaService).registrar(eq(pm), eq("CAMBIAR_PASSWORD"), eq("USUARIO"), eq(1L), anyString());
        }
    }
}
