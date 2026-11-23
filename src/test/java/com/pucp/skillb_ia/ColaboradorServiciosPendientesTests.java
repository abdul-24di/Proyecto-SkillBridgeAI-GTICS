package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.ColProyectoDetalleView;
import com.pucp.skillb_ia.dto.auth.ArchivoPendiente;
import com.pucp.skillb_ia.dto.auth.ErrorPreRegistro;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.EstadoEntrega;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.col.ColDesempenoService;
import com.pucp.skillb_ia.service.col.ColDesempenoService.Desempeno;
import com.pucp.skillb_ia.service.col.ColaboradorCursoService;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import com.pucp.skillb_ia.service.col.ColaboradorProyectoService;
import com.pucp.skillb_ia.service.col.PreRegistroArchivosService;
import com.pucp.skillb_ia.service.col.PreRegistroColaboradorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@SpringBootTest
@ActiveProfiles("test")
class ColaboradorServiciosPendientesTests {

    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private CursoRepository cursoRepository;
    @Autowired private ColaboradorCursoRepository colaboradorCursoRepository;
    @Autowired private CategoriaHabilidadRepository categoriaHabilidadRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ColaboradorProyectoService proyectoService;
    @Autowired private ColaboradorCursoService cursoService;
    @Autowired private ColDesempenoService desempenoService;
    @Autowired private ColaboradorPerfilService perfilService;
    @Autowired private PreRegistroColaboradorService preRegistroService;
    @Autowired private PreRegistroArchivosService preRegistroArchivosService;

    // ======================= ColaboradorProyectoService.obtenerDetalleProyecto =======================

    @Test
    void elDetalleDeUnaAsignacionInexistenteSeRechaza() {
        Usuario col = usuario("col", "COLABORADOR");

        assertThatThrownBy(() -> proyectoService.obtenerDetalleProyecto(col, 999999999L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El proyecto que buscas no existe.");
    }

    @Test
    void noSePuedeVerElDetalleDeLaAsignacionDeOtroColaborador() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Usuario intruso = usuario("intruso", "COLABORADOR");
        Proyecto proyecto = proyecto(pm, EstadoProyecto.ACTIVO);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.ACTIVA);

        assertThatThrownBy(() -> proyectoService.obtenerDetalleProyecto(intruso, asignacion.getId(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a este proyecto.");
    }

    @Test
    void quienFueDesasignadoConElProyectoAunActivoYaNoVeElDetalle() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm, EstadoProyecto.ACTIVO);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.FINALIZADA);

        assertThatThrownBy(() -> proyectoService.obtenerDetalleProyecto(col, asignacion.getId(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya no tienes acceso a este proyecto: fuiste desasignado.");
    }

    @Test
    void conElProyectoFinalizadoLaAsignacionFinalizadaSigueSiendoVisible() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm, EstadoProyecto.FINALIZADO);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.FINALIZADA);

        ColProyectoDetalleView detalle = proyectoService.obtenerDetalleProyecto(col, asignacion.getId(), null, null);

        assertThat(detalle.getProyecto().getId()).isEqualTo(proyecto.getId());
        assertThat(detalle.getAsignacion().getId()).isEqualTo(asignacion.getId());
    }

    @Test
    void elDetalleMuestraIntegrantesActivosYSoloMisActividadesEnMisActividades() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Usuario companero = usuario("companero", "COLABORADOR");
        Proyecto proyecto = proyecto(pm, EstadoProyecto.ACTIVO);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        asignar(companero, proyecto, EstadoAsignacion.ACTIVA);
        Actividad mia = actividad(proyecto, col, pm, 0);
        Actividad ajena = actividad(proyecto, companero, pm, 1);

        ColProyectoDetalleView detalle = proyectoService.obtenerDetalleProyecto(col, asignacion.getId(), null, null);

        assertThat(detalle.getIntegrantes()).hasSize(2);
        assertThat(detalle.getTotalMisActividades()).isEqualTo(1);
        assertThat(detalle.getTotalActividadesProyecto()).isEqualTo(2);
        assertThat(detalle.getMisActividades()).hasSize(1);
        assertThat(detalle.getMisActividades().get(0).getId()).isEqualTo(mia.getId());
        assertThat(ajena.getId()).isNotEqualTo(mia.getId());
        assertThat(detalle.getMisStrikes()).isZero();
    }

    @Test
    void lasActividadesSePaginanDeCincoEnCinco() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm, EstadoProyecto.ACTIVO);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        for (int i = 0; i < 7; i++) {
            actividad(proyecto, col, pm, i);
        }

        ColProyectoDetalleView primera = proyectoService.obtenerDetalleProyecto(col, asignacion.getId(), null, null);
        ColProyectoDetalleView segunda = proyectoService.obtenerDetalleProyecto(col, asignacion.getId(), "2", "2");

        assertThat(primera.getTotalPaginasMias()).isEqualTo(2);
        assertThat(primera.getTotalPaginasTodas()).isEqualTo(2);
        assertThat(primera.getPaginaActualMias()).isEqualTo(1);
        assertThat(primera.getMisActividades()).hasSize(ColaboradorProyectoService.TAMANIO_PAGINA_ACTIVIDADES);
        assertThat(primera.getActividadesDelProyecto()).hasSize(5);
        assertThat(segunda.getPaginaActualMias()).isEqualTo(2);
        assertThat(segunda.getPaginaActualTodas()).isEqualTo(2);
        assertThat(segunda.getMisActividades()).hasSize(2);
        assertThat(segunda.getActividadesDelProyecto()).hasSize(2);
    }

    @Test
    void unaPaginaInvalidaOFueraDeRangoSeCorrige() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm, EstadoProyecto.ACTIVO);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        for (int i = 0; i < 7; i++) {
            actividad(proyecto, col, pm, i);
        }

        ColProyectoDetalleView texto = proyectoService.obtenerDetalleProyecto(col, asignacion.getId(), "abc", "xyz");
        ColProyectoDetalleView enorme = proyectoService.obtenerDetalleProyecto(col, asignacion.getId(), "99", "99");
        ColProyectoDetalleView cero = proyectoService.obtenerDetalleProyecto(col, asignacion.getId(), "0", "-3");

        assertThat(texto.getPaginaActualTodas()).isEqualTo(1);
        assertThat(texto.getPaginaActualMias()).isEqualTo(1);
        assertThat(enorme.getPaginaActualTodas()).isEqualTo(2);
        assertThat(enorme.getPaginaActualMias()).isEqualTo(2);
        assertThat(cero.getPaginaActualTodas()).isEqualTo(1);
        assertThat(cero.getPaginaActualMias()).isEqualTo(1);
    }

    @Test
    void sinActividadesElDetalleTieneUnaSolaPaginaVacia() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm, EstadoProyecto.ACTIVO);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.ACTIVA);

        ColProyectoDetalleView detalle = proyectoService.obtenerDetalleProyecto(col, asignacion.getId(), null, null);

        assertThat(detalle.getTotalPaginasMias()).isEqualTo(1);
        assertThat(detalle.getTotalPaginasTodas()).isEqualTo(1);
        assertThat(detalle.getMisActividades()).isEmpty();
        assertThat(detalle.getActividadesDelProyecto()).isEmpty();
    }

    // ======================= ColaboradorProyectoService.validarEvidencia =======================

    @Test
    void laEvidenciaVaciaONulaSeRechaza() {
        String mensaje = "Debes adjuntar una evidencia para marcar la actividad como lista.";

        assertThat(proyectoService.validarEvidencia(null)).isEqualTo(mensaje);
        assertThat(proyectoService.validarEvidencia(evidencia("a.pdf", "application/pdf", new byte[0])))
                .isEqualTo(mensaje);
    }

    @Test
    void laEvidenciaSoloAceptaPdfJpgOPng() {
        assertThat(proyectoService.validarEvidencia(evidencia("a.gif", "image/gif", new byte[]{1})))
                .isEqualTo("La evidencia debe estar en formato PDF, JPG o PNG.");
        assertThat(proyectoService.validarEvidencia(evidencia("a.txt", null, new byte[]{1})))
                .isEqualTo("La evidencia debe estar en formato PDF, JPG o PNG.");
    }

    @Test
    void laExtensionDelNombreTambienDebeSerPermitida() {
        String mensaje = "La evidencia debe ser un archivo .pdf, .jpg o .png.";

        assertThat(proyectoService.validarEvidencia(evidencia("virus.exe", "application/pdf", new byte[]{1})))
                .isEqualTo(mensaje);
        assertThat(proyectoService.validarEvidencia(evidencia("sinextension", "application/pdf", new byte[]{1})))
                .isEqualTo(mensaje);
        assertThat(proyectoService.validarEvidencia(evidencia("termina.", "image/png", new byte[]{1})))
                .isEqualTo(mensaje);
    }

    @Test
    void laEvidenciaNoPuedeSuperarLosDiezMegas() {
        byte[] enorme = new byte[(int) ColaboradorProyectoService.TAMANO_MAXIMO_EVIDENCIA_BYTES + 1];

        assertThat(proyectoService.validarEvidencia(evidencia("grande.pdf", "application/pdf", enorme)))
                .isEqualTo("La evidencia supera el máximo de 10MB.");
    }

    @Test
    void unaEvidenciaValidaDevuelveNull() {
        assertThat(proyectoService.validarEvidencia(evidencia("ok.pdf", "application/pdf", new byte[]{1}))).isNull();
        assertThat(proyectoService.validarEvidencia(evidencia("OK.JPG", "image/jpeg", new byte[]{1}))).isNull();
        assertThat(proyectoService.validarEvidencia(evidencia("ok.png", "image/png", new byte[]{1}))).isNull();
    }

    // ======================= ColaboradorCursoService =======================

    @Test
    void listarCategoriasDevuelveLasDeCursosActivosSinRepetirNiVacias() {
        Usuario admin = usuario("admin", "ADMINISTRADOR");
        String unica = "cat-" + UUID.randomUUID().toString().substring(0, 8);
        String inactiva = "cat-inactiva-" + UUID.randomUUID().toString().substring(0, 8);
        curso(admin, unica, LocalDate.now().plusDays(10), true);
        curso(admin, unica, LocalDate.now().plusDays(10), true);
        curso(admin, "   ", LocalDate.now().plusDays(10), true);
        curso(admin, null, LocalDate.now().plusDays(10), true);
        curso(admin, inactiva, LocalDate.now().plusDays(10), false);

        List<String> categorias = cursoService.listarCategorias();

        int veces = 0;
        for (String categoria : categorias) {
            if (unica.equals(categoria)) {
                veces++;
            }
            assertThat(categoria).isNotBlank();
        }
        assertThat(veces).isEqualTo(1);
        assertThat(categorias).doesNotContain(inactiva);
    }

    @Test
    void elCursoEnCursoVencidoSinEvidenciaPasaANoCompletadoYElDeOtroColaboradorNoCambiaConLaVersionIndividual() {
        Usuario admin = usuario("admin", "ADMINISTRADOR");
        Usuario col = usuario("col", "COLABORADOR");
        Usuario otro = usuario("otro", "COLABORADOR");
        Curso vencido = curso(admin, "Técnico", LocalDate.now().minusDays(1), true);
        ColaboradorCurso mio = inscripcion(col, vencido, EstadoColaboradorCurso.EN_CURSO, admin);
        ColaboradorCurso delOtro = inscripcion(otro, vencido, EstadoColaboradorCurso.EN_CURSO, admin);

        cursoService.marcarCursosSinEvidenciaVencidos(col);

        assertThat(estadoDe(mio)).isEqualTo(EstadoColaboradorCurso.NO_COMPLETADO);
        assertThat(estadoDe(delOtro)).isEqualTo(EstadoColaboradorCurso.EN_CURSO);
    }

    @Test
    void marcarTodosLosVencidosSoloTocaLosEnCursoSinEvidenciaYConFechaFinPasada() {
        Usuario admin = usuario("admin", "ADMINISTRADOR");
        Usuario col = usuario("col", "COLABORADOR");
        ColaboradorCurso vencido = inscripcion(col,
                curso(admin, "Técnico", LocalDate.now().minusDays(2), true), EstadoColaboradorCurso.EN_CURSO, admin);
        ColaboradorCurso conEvidencia = inscripcion(col,
                curso(admin, "Técnico", LocalDate.now().minusDays(2), true), EstadoColaboradorCurso.EN_CURSO, admin);
        conEvidencia.setEvidenciaUrl("evidencias/previa.pdf");
        colaboradorCursoRepository.save(conEvidencia);
        ColaboradorCurso sinFechaFin = inscripcion(col,
                curso(admin, "Técnico", null, true), EstadoColaboradorCurso.EN_CURSO, admin);
        ColaboradorCurso conPlazo = inscripcion(col,
                curso(admin, "Técnico", LocalDate.now().plusDays(20), true), EstadoColaboradorCurso.EN_CURSO, admin);
        ColaboradorCurso completado = inscripcion(col,
                curso(admin, "Técnico", LocalDate.now().minusDays(2), true), EstadoColaboradorCurso.COMPLETADO, admin);

        cursoService.marcarTodosLosCursosSinEvidenciaVencidos();

        assertThat(estadoDe(vencido)).isEqualTo(EstadoColaboradorCurso.NO_COMPLETADO);
        assertThat(estadoDe(conEvidencia)).isEqualTo(EstadoColaboradorCurso.EN_CURSO);
        assertThat(estadoDe(sinFechaFin)).isEqualTo(EstadoColaboradorCurso.EN_CURSO);
        assertThat(estadoDe(conPlazo)).isEqualTo(EstadoColaboradorCurso.EN_CURSO);
        assertThat(estadoDe(completado)).isEqualTo(EstadoColaboradorCurso.COMPLETADO);
    }

    // ======================= ColDesempenoService: Excel y PDF =======================

    @Test
    void elReporteExcelEsUnArchivoXlsxValido() {
        Usuario col = colaboradorConActividadYCursoCompletados();
        Desempeno desempeno = desempenoService.obtener(col, YearMonth.now());

        byte[] excel = desempenoService.excel(col, desempeno);

        assertThat(excel).isNotEmpty();
        // Un .xlsx es un ZIP: empieza con "PK".
        assertThat(excel[0]).isEqualTo((byte) 'P');
        assertThat(excel[1]).isEqualTo((byte) 'K');
    }

    @Test
    void elReportePdfEmpiezaConLaFirmaPdf() {
        Usuario col = colaboradorConActividadYCursoCompletados();
        Desempeno desempeno = desempenoService.obtener(col, YearMonth.now());

        byte[] pdf = desempenoService.pdf(col, desempeno);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void losReportesTambienSeGeneranParaUnColaboradorSinActividad() {
        Usuario col = usuario("col", "COLABORADOR");
        Desempeno desempeno = desempenoService.obtener(col, YearMonth.now());

        assertThat(desempenoService.excel(col, desempeno)).isNotEmpty();
        assertThat(desempenoService.pdf(col, desempeno)).isNotEmpty();
        assertThat(desempeno.completadas()).isZero();
        assertThat(desempeno.puntualidad()).isZero();
    }

    @Test
    void elDesempenoCuentaLaActividadYElCursoCompletadosDelMesYSuPuntualidad() {
        Usuario col = colaboradorConActividadYCursoCompletados();

        Desempeno desempeno = desempenoService.obtener(col, YearMonth.now());

        assertThat(desempeno.completadas()).isEqualTo(1);
        assertThat(desempeno.aTiempo()).isEqualTo(1);
        assertThat(desempeno.tardias()).isZero();
        assertThat(desempeno.puntualidad()).isEqualTo(100);
        assertThat(desempeno.horas()).isEqualByComparingTo(new BigDecimal("4"));
        assertThat(desempeno.porProyecto()).hasSize(1);
        assertThat(desempeno.cursos()).hasSize(1);
        assertThat(desempeno.cursos().get(0).horas()).isEqualByComparingTo(new BigDecimal("12"));
    }

    // ======================= ColaboradorPerfilService: certificados y catálogo =======================

    @Test
    void elCertificadoVacioONuloSeRechaza() {
        assertThatThrownBy(() -> perfilService.validarArchivoCertificado(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes adjuntar un certificado.");
        assertThatThrownBy(() -> perfilService.validarArchivoCertificado(
                evidencia("c.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes adjuntar un certificado.");
    }

    @Test
    void elCertificadoSoloAceptaPdfJpgOPng() {
        assertThatThrownBy(() -> perfilService.validarArchivoCertificado(
                evidencia("c.gif", "image/gif", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El certificado debe estar en formato PDF, JPG o PNG.");
    }

    @Test
    void elCertificadoNoPuedeSuperarLosDiezMegas() {
        byte[] enorme = new byte[10 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> perfilService.validarArchivoCertificado(
                evidencia("c.pdf", "application/pdf", enorme)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El certificado supera el máximo de 10MB.");
    }

    @Test
    void elCertificadoCuyoContenidoNoEsUnPdfRealSeRechaza() {
        assertThatThrownBy(() -> perfilService.validarArchivoCertificado(
                evidencia("falso.pdf", "application/pdf", new byte[]{1, 2, 3})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El contenido del archivo no corresponde a un PDF válido.");
    }

    @Test
    void unCertificadoPdfPngOJpgRealesSonValidos() {
        byte[] pdf = "%PDF-1.4 contenido".getBytes();
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2};
        byte[] jpg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2};

        assertThatCode(() -> perfilService.validarArchivoCertificado(evidencia("c.pdf", "application/pdf", pdf)))
                .doesNotThrowAnyException();
        assertThatCode(() -> perfilService.validarArchivoCertificado(evidencia("c.png", "image/png", png)))
                .doesNotThrowAnyException();
        assertThatCode(() -> perfilService.validarArchivoCertificado(evidencia("c.jpg", "image/jpeg", jpg)))
                .doesNotThrowAnyException();
    }

    @Test
    void elCatalogoSoloListaCategoriasYHabilidadesActivas() {
        CategoriaHabilidad activa = categoria(true);
        CategoriaHabilidad inactiva = categoria(false);
        Habilidad habilidadActiva = habilidad(activa, true);
        Habilidad habilidadInactiva = habilidad(activa, false);

        List<CategoriaHabilidad> categorias = perfilService.listarCategoriasHabilidad();
        List<Habilidad> habilidades = perfilService.listarHabilidadesActivas();

        assertThat(contieneCategoria(categorias, activa)).isTrue();
        assertThat(contieneCategoria(categorias, inactiva)).isFalse();
        assertThat(contieneHabilidad(habilidades, habilidadActiva)).isTrue();
        assertThat(contieneHabilidad(habilidades, habilidadInactiva)).isFalse();
    }

    // ======================= PreRegistroColaboradorService.resumirErrores =======================

    @Test
    void losErroresSeAgrupanPorPasoConSuEtiquetaYSinRepetirCampo() {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "form");
        result.addError(new FieldError("form", "password", "La contraseña es débil."));
        result.addError(new FieldError("form", "dni", "DNI inválido."));
        result.addError(new FieldError("form", "dni", "Otro mensaje del mismo campo."));
        result.addError(new FieldError("form", "cv", "Adjunta tu CV."));
        result.addError(new FieldError("form", "certificados[0].archivo", "Falta el archivo."));
        result.addError(new FieldError("form", "experiencias[1].cargo", "Falta el cargo."));

        List<ErrorPreRegistro> errores = preRegistroService.resumirErrores(result);

        assertThat(errores).hasSize(5);
        // Ordenados por paso: 1 (DNI), 2 (CV y experiencia), 3 (certificado), 4 (contraseña).
        assertThat(errores.get(0).paso()).isEqualTo(1);
        assertThat(errores.get(0).etiqueta()).isEqualTo("DNI");
        assertThat(errores.get(0).mensaje()).isEqualTo("DNI inválido.");
        assertThat(errores.get(1).paso()).isEqualTo(2);
        assertThat(errores.get(2).paso()).isEqualTo(2);
        assertThat(errores.get(3).paso()).isEqualTo(3);
        assertThat(errores.get(3).etiqueta()).isEqualTo("Certificado 1 · Archivo");
        assertThat(errores.get(4).paso()).isEqualTo(4);
        assertThat(errores.get(4).etiqueta()).isEqualTo("Contraseña");
    }

    @Test
    void laFilaDeUnaExperienciaMuestraSuNumeroYSuColumna() {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "form");
        result.addError(new FieldError("form", "experiencias[1].cargo", "Falta el cargo."));
        result.addError(new FieldError("form", "estudios[0].titulo", "Falta el título."));

        List<ErrorPreRegistro> errores = preRegistroService.resumirErrores(result);

        assertThat(errores).hasSize(2);
        assertThat(errores.get(0).etiqueta()).isEqualTo("Experiencia 2 · Cargo");
        assertThat(errores.get(0).paso()).isEqualTo(2);
        assertThat(errores.get(1).etiqueta()).isEqualTo("Formación 1 · Título o carrera");
        assertThat(errores.get(1).paso()).isEqualTo(3);
    }

    @Test
    void elErrorDeContrasenasQueNoCoincidenEnfocaElCampoConfirmar() {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "form");
        result.addError(new FieldError("form", "passwordsCoinciden", "Las contraseñas no coinciden."));

        List<ErrorPreRegistro> errores = preRegistroService.resumirErrores(result);

        assertThat(errores).hasSize(1);
        assertThat(errores.get(0).campo()).isEqualTo("confirmarPassword");
        assertThat(errores.get(0).etiqueta()).isEqualTo("Confirmar contraseña");
        assertThat(errores.get(0).paso()).isEqualTo(4);
    }

    @Test
    void sinErroresElResumenEstaVacio() {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "form");

        assertThat(preRegistroService.resumirErrores(result)).isEmpty();
    }

    // ======================= PreRegistroArchivosService =======================

    @Test
    void unArchivoGuardadoSePuedeAbrirDesdeLaMismaSesion() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        byte[] contenido = "%PDF-1.4 mi cv".getBytes();

        ArchivoPendiente pendiente = preRegistroArchivosService.guardar(
                evidencia("mi-cv.pdf", "application/pdf", contenido), sesion);
        MultipartFile abierto = preRegistroArchivosService.abrir(pendiente.id(), sesion);

        assertThat(pendiente.nombre()).isEqualTo("mi-cv.pdf");
        assertThat(pendiente.tipo()).isEqualTo("application/pdf");
        assertThat(pendiente.tamano()).isEqualTo(contenido.length);
        assertThat(preRegistroArchivosService.archivos(sesion)).containsKey(pendiente.id());
        assertThat(abierto).isNotNull();
        assertThat(abierto.getBytes()).isEqualTo(contenido);
        assertThat(abierto.getOriginalFilename()).isEqualTo("mi-cv.pdf");

        preRegistroArchivosService.limpiar(sesion);
    }

    @Test
    void unIdDesconocidoONuloNoAbreNada() {
        MockHttpSession sesion = new MockHttpSession();

        assertThat(preRegistroArchivosService.abrir(UUID.randomUUID().toString(), sesion)).isNull();
        assertThat(preRegistroArchivosService.abrir(null, sesion)).isNull();
    }

    @Test
    void nadiePuedeAbrirElArchivoDeOtraSesion() throws Exception {
        MockHttpSession duena = new MockHttpSession();
        MockHttpSession otra = new MockHttpSession();
        ArchivoPendiente pendiente = preRegistroArchivosService.guardar(
                evidencia("cv.pdf", "application/pdf", "%PDF-1.4".getBytes()), duena);

        assertThat(preRegistroArchivosService.abrir(pendiente.id(), otra)).isNull();
        assertThat(preRegistroArchivosService.abrir(pendiente.id(), duena)).isNotNull();

        preRegistroArchivosService.limpiar(duena);
    }

    @Test
    void limpiarBorraLosArchivosYLosQuitaDeLaSesion() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        ArchivoPendiente pendiente = preRegistroArchivosService.guardar(
                evidencia("cv.pdf", "application/pdf", "%PDF-1.4".getBytes()), sesion);

        preRegistroArchivosService.limpiar(sesion);

        assertThat(preRegistroArchivosService.archivos(sesion)).isEmpty();
        assertThat(preRegistroArchivosService.abrir(pendiente.id(), sesion)).isNull();
    }

    @Test
    void elNombreVisibleSeLimpiaDeRutasYSeRecorta() throws Exception {
        MockHttpSession sesion = new MockHttpSession();

        ArchivoPendiente conRuta = preRegistroArchivosService.guardar(
                evidencia("C:\\fakepath\\cv.pdf", "application/pdf", new byte[]{1}), sesion);
        ArchivoPendiente sinNombre = preRegistroArchivosService.guardar(
                evidencia("   ", "application/pdf", new byte[]{1}), sesion);
        ArchivoPendiente largo = preRegistroArchivosService.guardar(
                evidencia("a".repeat(120) + ".pdf", "application/pdf", new byte[]{1}), sesion);

        assertThat(conRuta.nombre()).isEqualTo("cv.pdf");
        assertThat(sinNombre.nombre()).isEqualTo("archivo");
        assertThat(largo.nombre()).hasSize(80);

        preRegistroArchivosService.limpiar(sesion);
    }

    @Test
    void unaSesionNoPuedeGuardarMasDeCuarentaArchivos() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        for (int i = 0; i < 40; i++) {
            preRegistroArchivosService.guardar(evidencia("cv" + i + ".pdf", "application/pdf", new byte[]{1}), sesion);
        }

        assertThatThrownBy(() -> preRegistroArchivosService.guardar(
                evidencia("uno-mas.pdf", "application/pdf", new byte[]{1}), sesion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Subiste demasiados archivos. Vuelve a abrir el enlace de activación.");

        preRegistroArchivosService.limpiar(sesion);
    }

    // ======================= Ayudas =======================

    private Usuario colaboradorConActividadYCursoCompletados() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario admin = usuario("admin", "ADMINISTRADOR");
        Usuario col = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm, EstadoProyecto.ACTIVO);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA);

        Actividad actividad = actividad(proyecto, col, pm, 0);
        actividad.setEstado(EstadoActividad.COMPLETADA);
        actividad.setEstadoEntrega(EstadoEntrega.A_TIEMPO);
        actividad.setFechaEntrega(LocalDateTime.now());
        actividadRepository.save(actividad);

        Curso curso = curso(admin, "Técnico", null, true);
        curso.setHoras(new BigDecimal("12"));
        cursoRepository.save(curso);
        ColaboradorCurso completado = inscripcion(col, curso, EstadoColaboradorCurso.COMPLETADO, admin);
        completado.setFechaCompletado(LocalDateTime.now());
        colaboradorCursoRepository.save(completado);
        return col;
    }

    private EstadoColaboradorCurso estadoDe(ColaboradorCurso inscripcion) {
        return colaboradorCursoRepository.findById(inscripcion.getId()).orElseThrow().getEstado();
    }

    private boolean contieneCategoria(List<CategoriaHabilidad> lista, CategoriaHabilidad buscada) {
        for (CategoriaHabilidad categoria : lista) {
            if (categoria.getId().equals(buscada.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean contieneHabilidad(List<Habilidad> lista, Habilidad buscada) {
        for (Habilidad habilidad : lista) {
            if (habilidad.getId().equals(buscada.getId())) {
                return true;
            }
        }
        return false;
    }

    private MockMultipartFile evidencia(String nombre, String tipo, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, tipo, contenido);
    }

    private CategoriaHabilidad categoria(boolean activa) {
        CategoriaHabilidad c = new CategoriaHabilidad();
        c.setNombre("cat-" + UUID.randomUUID().toString().substring(0, 8));
        c.setActiva(activa);
        return categoriaHabilidadRepository.save(c);
    }

    private Habilidad habilidad(CategoriaHabilidad categoria, boolean activa) {
        Habilidad h = new Habilidad();
        h.setNombre("hab-" + UUID.randomUUID().toString().substring(0, 8));
        h.setCategoria(categoria);
        h.setActiva(activa);
        return habilidadRepository.save(h);
    }

    private Curso curso(Usuario creador, String categoria, LocalDate fechaFin, boolean activo) {
        Curso c = new Curso();
        c.setNombre("Curso pendiente " + UUID.randomUUID().toString().substring(0, 8));
        c.setDescripcion("Curso de prueba.");
        c.setCategoria(categoria);
        c.setHoras(new BigDecimal("8.00"));
        c.setFechaFin(fechaFin);
        c.setActivo(activo);
        c.setCreadoPor(creador);
        return cursoRepository.save(c);
    }

    private ColaboradorCurso inscripcion(Usuario colaborador, Curso curso, EstadoColaboradorCurso estado,
                                         Usuario asignador) {
        ColaboradorCurso item = new ColaboradorCurso();
        item.setColaborador(colaborador);
        item.setCurso(curso);
        item.setOrigen(OrigenCurso.SOLICITUD_COLABORADOR);
        item.setEstado(estado);
        item.setAsignadoPor(asignador);
        item.setFechaRespuesta(LocalDateTime.now());
        return colaboradorCursoRepository.save(item);
    }

    private Actividad actividad(Proyecto proyecto, Usuario colaborador, Usuario pm, int minutosAtras) {
        Actividad a = new Actividad();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setCreadoPor(pm);
        a.setTitulo("Actividad " + System.nanoTime());
        a.setHorasEstimadas(new BigDecimal("4"));
        a.setFechaAsignacion(LocalDateTime.now().minusMinutes(minutosAtras));
        a.setFechaLimite(LocalDate.now().plusDays(10));
        a.setEstado(EstadoActividad.EN_PROGRESO);
        return actividadRepository.save(a);
    }

    private Asignacion asignar(Usuario colaborador, Proyecto proyecto, EstadoAsignacion estado) {
        Asignacion a = new Asignacion();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setHorasSemanales(new BigDecimal("8"));
        a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        a.setEstado(estado);
        a.setAprobadoPorPm(true);
        a.setAprobadoPorRm(true);
        return asignacionRepository.save(a);
    }

    private Proyecto proyecto(Usuario pm, EstadoProyecto estado) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto pendiente " + System.nanoTime());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(estado);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(3);
        p.setPm(pm);
        p.setPresupuesto(new BigDecimal("10000.00"));
        p.setFechaInicio(LocalDate.now().minusMonths(1));
        p.setFechaFinEstimada(LocalDate.now().plusMonths(1));
        return proyectoRepository.save(p);
    }

    private Usuario usuario(String prefijo, String nombreRol) {
        Usuario u = new Usuario();
        u.setCorreo(prefijo + "." + UUID.randomUUID() + "@test.local");
        u.setNombre(prefijo);
        u.setApellido("Prueba");
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
}