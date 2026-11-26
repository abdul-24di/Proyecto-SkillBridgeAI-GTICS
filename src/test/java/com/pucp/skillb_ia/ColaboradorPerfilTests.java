package com.pucp.skillb_ia;


import com.pucp.skillb_ia.dto.auth.*;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ColaboradorPerfilTests {

    private static final String PASSWORD_ACTUAL = "Actual123!";

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private CategoriaHabilidadRepository categoriaHabilidadRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Autowired private EducacionRepository educacionRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ColaboradorPerfilService perfilService;

    // ======================= Sobre mí =======================

    @Test
    void sobreMiSeGuardaSinEspaciosYQuedaEnLaAuditoria() {
        Usuario col = usuario("col", "COLABORADOR");

        perfilService.actualizarSobreMi(col, "   Soy backend developer.   ");

        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getDescripcion())
                .isEqualTo("Soy backend developer.");
        assertThat(logAuditoriaRepository.findTop20ByEntidadOrderByFechaHoraDesc("USUARIO"))
                .anyMatch(l -> "ACTUALIZAR_PERFIL".equals(l.getAccion()) && col.getId().equals(l.getEntidadId()));
    }

    @Test
    void sobreMiVacioOSoloEspaciosLimpiaLaDescripcion() {
        Usuario col = usuario("col", "COLABORADOR");
        perfilService.actualizarSobreMi(col, "Algo");

        perfilService.actualizarSobreMi(col, "     ");
        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getDescripcion()).isNull();

        perfilService.actualizarSobreMi(col, "Algo");
        perfilService.actualizarSobreMi(col, null);
        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getDescripcion()).isNull();
    }

    @Test
    void sobreMiNoPuedeSuperarLos500Caracteres() {
        Usuario col = usuario("col", "COLABORADOR");

        perfilService.actualizarSobreMi(col, "x".repeat(500)); // el límite exacto sí pasa

        assertThatThrownBy(() -> perfilService.actualizarSobreMi(col, "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La descripción no puede superar los 500 caracteres.");
    }

    // ======================= Teléfono =======================

    @Test
    void elTelefonoValidoSeLimpiaYSeGuarda() {
        Usuario col = usuario("col", "COLABORADOR");

        assertThat(perfilService.validarTelefono("  987654321 ")).isEqualTo("987654321");
        assertThat(perfilService.validarTelefono("+51987654321")).isEqualTo("+51987654321");

        perfilService.actualizarTelefono(col, " 987654321 ");

        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getTelefono()).isEqualTo("987654321");
    }

    @Test
    void elTelefonoVacioONuloPideIndicarlo() {
        for (String telefono : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> perfilService.validarTelefono(telefono))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Indica tu número de teléfono.");
        }
    }

    @Test
    void elTelefonoConFormatoInvalidoSeRechazaConLaPoliticaUnica() {
        String[] invalidos = {"12345", "98765 4321", "987-654-321", "(01)9876543", "abc1234567",
                "9".repeat(21), "++51987654321", "51987654321+"};
        for (String telefono : invalidos) {
            assertThatThrownBy(() -> perfilService.validarTelefono(telefono))
                    .as("teléfono %s", telefono)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(PoliticaTelefono.MENSAJE);
        }
    }

    @Test
    void unTelefonoInvalidoNoModificaElGuardado() {
        Usuario col = usuario("col", "COLABORADOR");
        perfilService.actualizarTelefono(col, "987654321");

        assertThatThrownBy(() -> perfilService.actualizarTelefono(col, "123"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getTelefono()).isEqualTo("987654321");
    }

    // ======================= Foto de perfil =======================

    @Test
    void laFotoJpgOPngValidaSeGuardaYQuedaEnLaAuditoria() {
        Usuario col = usuario("col", "COLABORADOR");

        perfilService.actualizarFoto(col, archivo("foto.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3}));

        String url = usuarioRepository.findById(col.getId()).orElseThrow().getFotoUrl();
        assertThat(url).isNotBlank().contains(".png");
        assertThat(logAuditoriaRepository.findTop20ByEntidadOrderByFechaHoraDesc("USUARIO"))
                .anyMatch(l -> "ACTUALIZAR_PERFIL".equals(l.getAccion()) && col.getId().equals(l.getEntidadId()));

        perfilService.actualizarFoto(col, archivo("foto.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3}));
        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getFotoUrl()).contains(".jpg");
    }

    @Test
    void laFotoSinArchivoConOtroFormatoOMuyPesadaSeRechaza() {
        Usuario col = usuario("col", "COLABORADOR");

        assertThatThrownBy(() -> perfilService.actualizarFoto(col, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona una imagen para subir.");
        assertThatThrownBy(() -> perfilService.actualizarFoto(col, archivo("foto.png", "image/png", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona una imagen para subir.");
        assertThatThrownBy(() -> perfilService.actualizarFoto(col, archivo("foto.gif", "image/gif", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Solo se aceptan imágenes JPG o PNG.");
        assertThatThrownBy(() -> perfilService.actualizarFoto(col,
                archivo("grande.png", "image/png", new byte[2 * 1024 * 1024 + 1])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La imagen supera el máximo de 2MB.");

        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getFotoUrl()).isNull();
    }

    @Test
    void sePuedeEliminarLaFotoSoloSiExiste() {
        Usuario col = usuario("col", "COLABORADOR");

        assertThatThrownBy(() -> perfilService.eliminarFoto(col))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes una foto de perfil para eliminar.");

        perfilService.actualizarFoto(col, archivo("foto.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3}));
        perfilService.eliminarFoto(col);

        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getFotoUrl()).isNull();
    }

    // ======================= CV =======================

    @Test
    void elCvSinArchivoConOtroFormatoPesadoOFalsoSeRechaza() {
        assertThatThrownBy(() -> perfilService.validarCv(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona un archivo PDF para subir.");
        assertThatThrownBy(() -> perfilService.validarCv(archivo("cv.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona un archivo PDF para subir.");
        assertThatThrownBy(() -> perfilService.validarCv(archivo("cv.png", "image/png", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El CV debe estar en formato PDF.");
        assertThatThrownBy(() -> perfilService.validarCv(
                archivo("cv.pdf", "application/pdf", pdfConTamano(5 * 1024 * 1024 + 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El archivo supera el máximo de 5MB.");
        // Dice ser PDF pero su contenido no lo es.
        assertThatThrownBy(() -> perfilService.validarCv(archivo("cv.pdf", "application/pdf", "hola".getBytes())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El contenido del archivo no corresponde a un PDF válido.");
    }

    @Test
    void alSubirElCvQuedaPendienteDeRevisionYAvisaALosAdministradores() {
        Usuario admin = usuario("admin", "ADMINISTRADOR");
        Usuario col = usuario("col", "COLABORADOR");
        col.setMotivoRechazo("CV ilegible");
        usuarioRepository.save(col);

        perfilService.actualizarCv(col, archivo("cv.pdf", "application/pdf", pdfConTamano(100)));

        Usuario guardado = usuarioRepository.findById(col.getId()).orElseThrow();
        assertThat(guardado.getCvUrl()).isNotBlank().endsWith(".pdf");
        assertThat(guardado.getCvEstado()).isEqualTo(EstadoCv.PENDIENTE);
        assertThat(guardado.getMotivoRechazo()).isNull();
        assertThat(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(admin))
                .anyMatch(n -> "CV_PENDIENTE".equals(n.getTipo()) && col.getId().equals(n.getEntidadId()));
    }

    @Test
    void unRegistroRechazadoVuelveARevisionAlSubirUnCvCorregido() {
        Usuario col = usuario("col", "COLABORADOR");
        col.setRegistroEstado(EstadoRegistro.RECHAZADO);
        usuarioRepository.save(col);

        perfilService.actualizarCv(col, archivo("cv.pdf", "application/pdf", pdfConTamano(100)));

        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getRegistroEstado())
                .isEqualTo(EstadoRegistro.PENDIENTE);
    }

    @Test
    void unCvInvalidoNoCambiaElEstadoDelColaborador() {
        Usuario col = usuario("col", "COLABORADOR");

        assertThatThrownBy(() -> perfilService.actualizarCv(col, archivo("cv.txt", "text/plain", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getCvUrl()).isNull();
    }

    // ======================= Cambio de contraseña =======================

    @Test
    void laContrasenaSeCambiaCuandoLaActualEsCorrectaYLaNuevaCumpleLaPolitica() {
        Usuario col = usuario("col", "COLABORADOR");

        perfilService.cambiarPassword(col, PASSWORD_ACTUAL, "Nueva456#", "Nueva456#");

        Usuario guardado = usuarioRepository.findById(col.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("Nueva456#", guardado.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD_ACTUAL, guardado.getPasswordHash())).isFalse();
        assertThat(logAuditoriaRepository.findTop20ByEntidadOrderByFechaHoraDesc("USUARIO"))
                .anyMatch(l -> "CAMBIO_PASSWORD".equals(l.getAccion()) && col.getId().equals(l.getEntidadId()));
    }

    @Test
    void conLaContrasenaActualIncorrectaONulaNoSeCambiaNada() {
        Usuario col = usuario("col", "COLABORADOR");

        assertThatThrownBy(() -> perfilService.cambiarPassword(col, "Incorrecta1!", "Nueva456#", "Nueva456#"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La contraseña actual no es correcta.");
        assertThatThrownBy(() -> perfilService.cambiarPassword(col, null, "Nueva456#", "Nueva456#"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La contraseña actual no es correcta.");

        assertThat(passwordEncoder.matches(PASSWORD_ACTUAL,
                usuarioRepository.findById(col.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void laContrasenaNuevaDebeCumplirLaPoliticaDeSeguridad() {
        Usuario col = usuario("col", "COLABORADOR");

        assertThatThrownBy(() -> perfilService.cambiarPassword(col, PASSWORD_ACTUAL, "Ab1!", "Ab1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La nueva contraseña debe tener al menos 8 caracteres.");
        assertThatThrownBy(() -> perfilService.cambiarPassword(col, PASSWORD_ACTUAL, "abcdefg1!", "abcdefg1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La nueva contraseña debe tener al menos una letra mayúscula.");
        assertThatThrownBy(() -> perfilService.cambiarPassword(col, PASSWORD_ACTUAL, "Abcdefgh!", "Abcdefgh!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La nueva contraseña debe tener al menos un número.");
        assertThatThrownBy(() -> perfilService.cambiarPassword(col, PASSWORD_ACTUAL, "Abcdefg12", "Abcdefg12"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La nueva contraseña debe tener al menos un símbolo (por ejemplo: !@#$%).");
        assertThatThrownBy(() -> perfilService.cambiarPassword(col, PASSWORD_ACTUAL, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La nueva contraseña debe tener al menos 8 caracteres.");
    }

    @Test
    void laConfirmacionDebeCoincidirConLaContrasenaNueva() {
        Usuario col = usuario("col", "COLABORADOR");

        assertThatThrownBy(() -> perfilService.cambiarPassword(col, PASSWORD_ACTUAL, "Nueva456#", "Otra456#"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Las contraseñas nuevas no coinciden.");

        assertThat(passwordEncoder.matches(PASSWORD_ACTUAL,
                usuarioRepository.findById(col.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    // ======================= Perfil completado =======================

    @Test
    void unPerfilVacioTieneCeroPorcientoYCuatroPendientes() {
        Usuario col = usuario("col", "COLABORADOR");

        assertThat(perfilService.calcularPorcentajeCompletado(col)).isZero();
        assertThat(perfilService.listarPendientesCompletar(col)).containsExactly(
                "Agrega una foto de perfil",
                "Agrega una descripción en \"Sobre mí\"",
                "Agrega una habilidad",
                "Agrega tu formación académica");
    }

    @Test
    void fotoYSobreMiSumanElCincuentaPorCiento() {
        Usuario col = usuario("col", "COLABORADOR");
        col.setFotoUrl("/uploads/perfil/foto.png");
        col.setDescripcion("Hola");
        usuarioRepository.save(col);

        assertThat(perfilService.calcularPorcentajeCompletado(col)).isEqualTo(50);
        assertThat(perfilService.listarPendientesCompletar(col)).hasSize(2);
    }

    @Test
    void unaHabilidadOFormacionSinValidarPideEsperarAlRm() {
        Usuario col = usuario("col", "COLABORADOR");
        habilidadDelColaborador(col, EstadoValidacion.PENDIENTE);
        educacion(col, EstadoCertificado.PENDIENTE);

        assertThat(perfilService.calcularPorcentajeCompletado(col)).isZero();
        assertThat(perfilService.listarPendientesCompletar(col)).contains(
                "Espera a que el RM valide alguna de tus habilidades",
                "Espera a que el RM apruebe tu formación académica");
    }

    @Test
    void conTodoValidadoElPerfilEstaAlCienPorCientoYSinPendientes() {
        Usuario col = usuario("col", "COLABORADOR");
        col.setFotoUrl("/uploads/perfil/foto.png");
        col.setDescripcion("Hola");
        usuarioRepository.save(col);
        habilidadDelColaborador(col, EstadoValidacion.VALIDADA);
        educacion(col, EstadoCertificado.APROBADO);

        assertThat(perfilService.calcularPorcentajeCompletado(col)).isEqualTo(100);
        assertThat(perfilService.listarPendientesCompletar(col)).isEmpty();
    }

    @Test
    void unaHabilidadOFormacionRechazadaOEliminadaNoCuentaParaElPerfil() {
        Usuario col = usuario("col", "COLABORADOR");
        habilidadDelColaborador(col, EstadoValidacion.RECHAZADA);
        Educacion eliminada = educacion(col, EstadoCertificado.APROBADO);
        eliminada.setActivo(false);
        educacionRepository.save(eliminada);

        assertThat(perfilService.calcularPorcentajeCompletado(col)).isZero();
    }

    // ---------- Utilidades ----------

    private MockMultipartFile archivo(String nombre, String tipo, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, tipo, contenido);
    }

    private byte[] pdfConTamano(int bytes) {
        byte[] datos = new byte[bytes];
        byte[] firma = "%PDF-1.4".getBytes();
        System.arraycopy(firma, 0, datos, 0, Math.min(firma.length, bytes));
        return datos;
    }

    private Usuario usuario(String prefijo, String nombreRol) {
        Usuario u = new Usuario();
        u.setCorreo(prefijo + "." + UUID.randomUUID() + "@test.local");
        u.setNombre(prefijo);
        u.setApellido("Prueba");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD_ACTUAL));
        u.setRol(rol(nombreRol));
        u.setActivo(true);
        u.setHorasContratadasSemana(new BigDecimal("40.00"));
        u.setHorasDisponibles(new BigDecimal("40.00"));
        return usuarioRepository.save(u);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombre);
            return rolRepository.save(r);
        });
    }

    private void habilidadDelColaborador(Usuario col, EstadoValidacion estado) {
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findAll().stream().findFirst().orElseGet(() -> {
            CategoriaHabilidad c = new CategoriaHabilidad();
            c.setNombre("Categoria " + UUID.randomUUID());
            return categoriaHabilidadRepository.save(c);
        });
        Habilidad h = new Habilidad();
        h.setNombre("Habilidad " + UUID.randomUUID().toString().substring(0, 6));
        h.setCategoria(categoria);
        h = habilidadRepository.save(h);

        ColaboradorHabilidad ch = new ColaboradorHabilidad();
        ch.setColaborador(col);
        ch.setHabilidad(h);
        ch.setNivelDominio(NivelDominio.INTERMEDIO);
        ch.setEstadoValidacion(estado);
        ch.setActivo(true);
        colaboradorHabilidadRepository.save(ch);
    }

    private Educacion educacion(Usuario col, EstadoCertificado estado) {
        Educacion e = new Educacion();
        e.setColaborador(col);
        e.setInstitucion("PUCP");
        e.setTitulo("Ingeniería");
        e.setEstado(estado);
        return educacionRepository.save(e);
    }
}