package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Perfil del RM: teléfono, foto y contraseña con sus validaciones, guardado y auditoría.
@ExtendWith(MockitoExtension.class)
class RmPerfilUnitTests {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private ArchivoAlmacenamientoService archivoAlmacenamientoService;

    @InjectMocks
    private RmPerfilService service;

    private Usuario yo;

    @BeforeEach
    void preparar() {
        yo = rm(1L);
        yo.setPasswordHash("hash-actual");
    }

    @Nested
    class ObtenerPerfil {

        @Test
        void devuelveElUsuario() {
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(yo));
            assertSame(yo, service.obtenerPerfil(1L));
        }

        @Test
        void usuarioInexistenteLanza() {
            when(usuarioRepository.findById(9L)).thenReturn(Optional.empty());
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.obtenerPerfil(9L));
            assertEquals("No se encontró el usuario autenticado.", ex.getMessage());
        }
    }

    @Nested
    class ActualizarTelefono {

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void vacioLanza(String telefono) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarTelefono(telefono, yo));
            assertEquals(RmPerfilService.MENSAJE_TELEFONO_OBLIGATORIO, ex.getMessage());
            verifyNoInteractions(usuarioRepository, auditoriaService);
        }

        @Test
        void nullLanza() {
            assertThrows(IllegalArgumentException.class, () -> service.actualizarTelefono(null, yo));
            verify(usuarioRepository, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"123456", "999 888 777", "(01)4567890", "+123456", "123456789012345678901", "9a8765432"})
        void formatoInvalidoLanza(String telefono) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarTelefono(telefono, yo));
            assertEquals(RmPerfilService.MENSAJE_TELEFONO_FORMATO, ex.getMessage());
            verify(usuarioRepository, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"1234567", "+51987654321", "12345678901234567890"})
        void bordesValidosSeGuardan(String telefono) {
            service.actualizarTelefono(telefono, yo);
            assertEquals(telefono, yo.getTelefono());
        }

        @Test
        void guardaElTelefonoSinEspaciosExterioresYAudita() {
            service.actualizarTelefono("  987654321 ", yo);

            assertEquals("987654321", yo.getTelefono());
            verify(usuarioRepository).save(yo);
            verify(auditoriaService).registrar(yo, "ACTUALIZAR_PERFIL", "USUARIO", 1L, "RM actualizó su teléfono.");
        }
    }

    @Nested
    class ActualizarFoto {

        private MockMultipartFile foto(String tipo, int bytes) {
            return new MockMultipartFile("foto", "foto", tipo, new byte[bytes]);
        }

        @Test
        void nullOVaciaLanza() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarFoto(null, yo));
            assertEquals("La foto no puede estar vacía.", ex.getMessage());
            assertThrows(IllegalArgumentException.class, () -> service.actualizarFoto(foto("image/png", 0), yo));
            verifyNoInteractions(archivoAlmacenamientoService);
        }

        @Test
        void tipoNoPermitidoLanza() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarFoto(foto("image/gif", 10), yo));
            assertEquals("Solo se permiten imágenes JPEG o PNG.", ex.getMessage());
            verifyNoInteractions(archivoAlmacenamientoService);
        }

        @Test
        void masDe2MbLanza() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarFoto(foto("image/jpeg", 2 * 1024 * 1024 + 1), yo));
            assertEquals("La foto no puede superar los 2 MB.", ex.getMessage());
            verifyNoInteractions(archivoAlmacenamientoService, usuarioRepository);
        }

        @Test
        void exactamente2MbSeAcepta() {
            MockMultipartFile f = foto("image/jpeg", 2 * 1024 * 1024);
            when(archivoAlmacenamientoService.guardar(eq(f), eq("perfil"), anyString())).thenReturn("/u/f.jpg");
            service.actualizarFoto(f, yo);
            assertEquals("/u/f.jpg", yo.getFotoUrl());
        }

        @Test
        void pngSeGuardaConExtensionPngGuardaUrlYAudita() {
            MockMultipartFile f = foto("image/png", 100);
            when(archivoAlmacenamientoService.guardar(eq(f), eq("perfil"), anyString())).thenAnswer(inv -> {
                String nombre = inv.getArgument(2);
                assertTrue(nombre.startsWith("usuario-1-") && nombre.endsWith(".png"), nombre);
                return "/uploads/perfil/" + nombre;
            });

            service.actualizarFoto(f, yo);

            assertTrue(yo.getFotoUrl().startsWith("/uploads/perfil/usuario-1-"));
            verify(usuarioRepository).save(yo);
            verify(auditoriaService).registrar(yo, "ACTUALIZAR_PERFIL", "USUARIO", 1L, "RM actualizó su foto de perfil.");
        }

        @Test
        void jpegSeGuardaConExtensionJpg() {
            MockMultipartFile f = foto("image/jpeg", 100);
            when(archivoAlmacenamientoService.guardar(eq(f), eq("perfil"), anyString()))
                    .thenAnswer(inv -> inv.getArgument(2));
            service.actualizarFoto(f, yo);
            assertTrue(yo.getFotoUrl().endsWith(".jpg"));
        }
    }

    @Nested
    class ActualizarPassword {

        @Test
        void passwordActualIncorrectaLanza() {
            when(passwordEncoder.matches("mala", "hash-actual")).thenReturn(false);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarPassword("mala", "Nueva#2026", yo));
            assertEquals("La contraseña actual es incorrecta.", ex.getMessage());
            verify(passwordEncoder, never()).encode(any());
            verifyNoInteractions(usuarioRepository, auditoriaService);
        }

        @ParameterizedTest
        @ValueSource(strings = {"Ab1#", "sinmayus1#", "SinNumero#", "SinSimbolo1", "Con Espacio1"})
        void nuevaQueNoCumpleLaPoliticaLanza(String nueva) {
            when(passwordEncoder.matches("buena", "hash-actual")).thenReturn(true);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarPassword("buena", nueva, yo));
            assertEquals("La nueva contraseña debe tener al menos 8 caracteres, una mayúscula, un número y un símbolo.",
                    ex.getMessage());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void nuevaNullLanza() {
            when(passwordEncoder.matches("buena", "hash-actual")).thenReturn(true);
            assertThrows(IllegalArgumentException.class, () -> service.actualizarPassword("buena", null, yo));
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void exitoCodificaGuardaYAudita() {
            when(passwordEncoder.matches("buena", "hash-actual")).thenReturn(true);
            when(passwordEncoder.encode("Nueva#2026")).thenReturn("hash-nuevo");

            service.actualizarPassword("buena", "Nueva#2026", yo);

            assertEquals("hash-nuevo", yo.getPasswordHash());
            verify(usuarioRepository).save(yo);
            verify(auditoriaService).registrar(yo, "CAMBIAR_PASSWORD", "USUARIO", 1L, "RM cambió su contraseña.");
        }
    }
}
