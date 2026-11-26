
package com.pucp.skillb_ia.dto;
import com.pucp.skillb_ia.dto.ColPerfilRequeridoView;
import com.pucp.skillb_ia.dto.ColProyectoAvanceView;
import com.pucp.skillb_ia.dto.ColProyectoDisponibleView;
import com.pucp.skillb_ia.dto.auth.PoliticaPassword;
import com.pucp.skillb_ia.dto.auth.PoliticaTelefono;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.service.col.ColaboradorActividadService;
import com.pucp.skillb_ia.service.col.ColaboradorProyectoService;
import com.pucp.skillb_ia.util.Paginador;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ColaboradorReglasTests {

    private ColaboradorProyectoService crearServicioSinDependencias() {
        return new ColaboradorProyectoService(null, null, null, null, null, null, null, null, null, null, null);
    }

    // =====================================================================
    // Teléfono del perfil del colaborador
    // =====================================================================

    @Test
    void telefonoConSoloDigitosEsValido() {
        assertTrue("987654321".matches(PoliticaTelefono.REGEX));
    }

    @Test
    void telefonoConMasAlInicioEsValido() {
        assertTrue("+51987654321".matches(PoliticaTelefono.REGEX));
    }

    @Test
    void telefonoMuyCortoEsInvalido() {
        assertFalse("123456".matches(PoliticaTelefono.REGEX));
    }

    @Test
    void telefonoConEspaciosEsInvalido() {
        assertFalse("987 654 321".matches(PoliticaTelefono.REGEX));
    }

    @Test
    void telefonoConLetrasEsInvalido() {
        assertFalse("98765abc21".matches(PoliticaTelefono.REGEX));
    }

    @Test
    void telefonoConGuionesEsInvalido() {
        assertFalse("987-654-321".matches(PoliticaTelefono.REGEX));
    }

    // =====================================================================
    // Contraseña al activar la cuenta
    // =====================================================================

    @Test
    void contrasenaFuerteEsValida() {
        assertTrue("Abcdef1!".matches(PoliticaPassword.REGEX));
    }

    @Test
    void contrasenaSinMayusculaEsInvalida() {
        assertFalse("abcdef1!".matches(PoliticaPassword.REGEX));
    }

    @Test
    void contrasenaSinNumeroEsInvalida() {
        assertFalse("Abcdefg!".matches(PoliticaPassword.REGEX));
    }

    @Test
    void contrasenaSinSimboloEsInvalida() {
        assertFalse("Abcdefg1".matches(PoliticaPassword.REGEX));
    }

    @Test
    void contrasenaConMenosDe8CaracteresEsInvalida() {
        assertFalse("Abc1!".matches(PoliticaPassword.REGEX));
    }

    // =====================================================================
    // Evidencia de una actividad (PDF, JPG o PNG, máximo 10MB)
    // =====================================================================

    @Test
    void evidenciaNulaPideAdjuntarArchivo() {
        ColaboradorProyectoService servicio = crearServicioSinDependencias();

        String error = servicio.validarEvidencia(null);

        assertEquals("Debes adjuntar una evidencia para marcar la actividad como lista.", error);
    }

    @Test
    void evidenciaVaciaPideAdjuntarArchivo() {
        ColaboradorProyectoService servicio = crearServicioSinDependencias();
        MockMultipartFile vacio = new MockMultipartFile("evidencia", "a.pdf", "application/pdf", new byte[0]);

        String error = servicio.validarEvidencia(vacio);

        assertEquals("Debes adjuntar una evidencia para marcar la actividad como lista.", error);
    }

    @Test
    void evidenciaDeTextoEsRechazada() {
        ColaboradorProyectoService servicio = crearServicioSinDependencias();
        MockMultipartFile texto = new MockMultipartFile("evidencia", "a.txt", "text/plain", "hola".getBytes());

        String error = servicio.validarEvidencia(texto);

        assertEquals("La evidencia debe estar en formato PDF, JPG o PNG.", error);
    }

    @Test
    void evidenciaConExtensionNoPermitidaEsRechazada() {
        ColaboradorProyectoService servicio = crearServicioSinDependencias();
        MockMultipartFile exe = new MockMultipartFile("evidencia", "virus.exe", "application/pdf", "hola".getBytes());

        String error = servicio.validarEvidencia(exe);

        assertEquals("La evidencia debe ser un archivo .pdf, .jpg o .png.", error);
    }

    @Test
    void evidenciaPdfValidaNoDevuelveError() {
        ColaboradorProyectoService servicio = crearServicioSinDependencias();
        MockMultipartFile pdf = new MockMultipartFile("evidencia", "informe.pdf", "application/pdf", "contenido".getBytes());

        assertNull(servicio.validarEvidencia(pdf));
    }

    @Test
    void evidenciaPngValidaNoDevuelveError() {
        ColaboradorProyectoService servicio = crearServicioSinDependencias();
        MockMultipartFile png = new MockMultipartFile("evidencia", "captura.png", "image/png", "contenido".getBytes());

        assertNull(servicio.validarEvidencia(png));
    }

    @Test
    void elTamanoMaximoDeEvidenciaEs10MB() {
        assertEquals(10L * 1024 * 1024, ColaboradorProyectoService.TAMANO_MAXIMO_EVIDENCIA_BYTES);
    }

    @Test
    void elComentarioDeEntregaTieneMaximo300Caracteres() {
        assertEquals(300, ColaboradorProyectoService.MAX_COMENTARIO_ENTREGA);
    }

    // =====================================================================
    // Meta mensual de horas
    // =====================================================================

    @Test
    void laMetaMensualDeHorasEs160() {
        assertEquals(new BigDecimal("160"), ColaboradorActividadService.META_MENSUAL_HORAS);
    }

    // =====================================================================
    // Valores iniciales de las entidades (estados con los que nace cada registro)
    // =====================================================================

    @Test
    void unaAsignacionNuevaNaceEnEstadoPendienteSinAprobaciones() {
        Asignacion asignacion = new Asignacion();

        assertEquals(EstadoAsignacion.PENDIENTE, asignacion.getEstado());
        assertFalse(asignacion.isAprobadoPorPm());
        assertFalse(asignacion.isAprobadoPorRm());
    }

    @Test
    void unaActividadNuevaNaceEnEstadoPendienteYActiva() {
        Actividad actividad = new Actividad();

        assertEquals(EstadoActividad.PENDIENTE, actividad.getEstado());
        assertTrue(actividad.isActivo());
        assertEquals(0, actividad.getVecesDevuelta());
        assertNull(actividad.getEstadoEntrega());
    }

    @Test
    void unProyectoNuevoNaceEnRevisionConUnColaboradorRequerido() {
        Proyecto proyecto = new Proyecto();

        assertEquals(EstadoProyecto.EN_REVISION, proyecto.getEstado());
        assertEquals(1, proyecto.getColaboradoresRequeridos());
    }

    @Test
    void unaHabilidadNuevaDelColaboradorNacePendienteDeValidacion() {
        ColaboradorHabilidad habilidad = new ColaboradorHabilidad();

        assertEquals(EstadoValidacion.PENDIENTE, habilidad.getEstadoValidacion());
        assertTrue(habilidad.isActivo());
    }

    @Test
    void unaSolicitudDeCursoNuevaNaceEnEstadoSolicitado() {
        ColaboradorCurso solicitud = new ColaboradorCurso();

        assertEquals(EstadoColaboradorCurso.SOLICITADO, solicitud.getEstado());
    }

    @Test
    void unUsuarioNuevoNaceActivoYSinDniVerificado() {
        Usuario usuario = new Usuario();

        assertTrue(usuario.isActivo());
        assertFalse(usuario.isDniVerificado());
    }

    // =====================================================================
    // Vistas de proyectos del colaborador
    // =====================================================================

    @Test
    void laVistaDePerfilRequeridoGuardaSusDatos() {
        ColPerfilRequeridoView perfil = new ColPerfilRequeridoView(5L, "Java (Avanzado)", 2, new BigDecimal("20"));

        assertEquals(5L, perfil.getHabilidadId());
        assertEquals("Java (Avanzado)", perfil.getEtiqueta());
        assertEquals(2, perfil.getCuposDisponibles());
        assertEquals(new BigDecimal("20"), perfil.getHorasSemanales());
    }

    @Test
    void laVistaDeAvanceGuardaElProyectoYElPorcentaje() {
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre("Portal de clientes");

        ColProyectoAvanceView avance = new ColProyectoAvanceView(proyecto, 75);

        assertEquals("Portal de clientes", avance.getProyecto().getNombre());
        assertEquals(75, avance.getPorcentajeAvance());
    }

    @Test
    void laVistaDeProyectoDisponibleGuardaCuposYBanderas() {
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre("App movil");
        List<ColPerfilRequeridoView> perfiles = new ArrayList<>();
        perfiles.add(new ColPerfilRequeridoView(1L, "Java", 1, new BigDecimal("20")));

        ColProyectoDisponibleView vista = new ColProyectoDisponibleView(proyecto, 1, 2, perfiles, true, false, true);

        assertEquals("App movil", vista.getProyecto().getNombre());
        assertEquals(1, vista.getColaboradoresActivos());
        assertEquals(2, vista.getCuposDisponibles());
        assertEquals(1, vista.getPerfiles().size());
        assertTrue(vista.isYaTieneSolicitud());
        assertFalse(vista.isTieneHorasSuficientes());
        assertTrue(vista.isPostulacionesAbiertas());
    }

    // =====================================================================
    // Paginación de listados (actividades, proyectos, cursos)
    // =====================================================================

    @Test
    void paginarUnaListaDe25ConTamano10DaTresPaginas() {
        List<Integer> numeros = crearLista(25);

        Page<Integer> pagina = Paginador.paginar(numeros, 1, 10);

        assertEquals(3, pagina.getTotalPages());
        assertEquals(25, pagina.getTotalElements());
        assertEquals(10, pagina.getContent().size());
    }

    @Test
    void laUltimaPaginaTieneLosElementosRestantes() {
        List<Integer> numeros = crearLista(25);

        Page<Integer> pagina = Paginador.paginar(numeros, 3, 10);

        assertEquals(5, pagina.getContent().size());
    }

    @Test
    void unaPaginaFueraDeRangoSeCorrigeALaUltima() {
        List<Integer> numeros = crearLista(25);

        Page<Integer> pagina = Paginador.paginar(numeros, 99, 10);

        assertEquals(5, pagina.getContent().size());
    }

    @Test
    void unaPaginaNulaMuestraLaPrimera() {
        List<Integer> numeros = crearLista(25);

        Page<Integer> pagina = Paginador.paginar(numeros, null, 10);

        assertEquals(10, pagina.getContent().size());
        assertEquals(Integer.valueOf(0), pagina.getContent().get(0));
    }

    @Test
    void unaListaVaciaDaUnaPaginaSinContenido() {
        List<Integer> vacia = new ArrayList<>();

        Page<Integer> pagina = Paginador.paginar(vacia, 1, 10);

        assertNotNull(pagina);
        assertEquals(0, pagina.getTotalPages());
        assertTrue(pagina.getContent().isEmpty());
    }

    private List<Integer> crearLista(int cantidad) {
        List<Integer> lista = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            lista.add(i);
        }
        return lista;
    }
}