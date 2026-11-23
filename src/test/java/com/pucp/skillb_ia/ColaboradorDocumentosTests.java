package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Documento;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaDocumento;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.DocumentoRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.col.ColaboradorDocumentoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Documentos de un proyecto del lado del colaborador (ColaboradorDocumentoService): listar con filtros,
// subir (validación y categoría automática) y eliminar. Se revierte todo al terminar cada prueba.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ColaboradorDocumentosTests {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private DocumentoRepository documentoRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private ColaboradorDocumentoService documentoService;

    private static final byte[] PDF = "%PDF-1.4 prueba".getBytes();

    @Test
    void laCategoriaDelDocumentoSeDeterminaPorSuExtension() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA, null);

        documentoService.subirDocumento(col, proyecto.getId(), archivo("informe.pdf", "application/pdf", PDF));
        documentoService.subirDocumento(col, proyecto.getId(), archivo("presupuesto.xlsx", "application/octet-stream", new byte[]{1, 2, 3}));
        documentoService.subirDocumento(col, proyecto.getId(), archivo("acta.docx", "application/octet-stream", new byte[]{1, 2, 3}));
        documentoService.subirDocumento(col, proyecto.getId(), archivo("captura.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1}));
        documentoService.subirDocumento(col, proyecto.getId(), archivo("maqueta.fig", "application/octet-stream", new byte[]{1, 2, 3}));

        List<Documento> documentos = documentoService.listarDocumentos(col, proyecto.getId(), null, null);

        assertThat(documentos).hasSize(5);
        assertThat(documentos).extracting(Documento::getCategoria).containsExactlyInAnyOrder(
                CategoriaDocumento.PDF, CategoriaDocumento.EXCEL, CategoriaDocumento.WORD,
                CategoriaDocumento.IMAGEN, CategoriaDocumento.DISENO);
        assertThat(documentos).allSatisfy(d -> {
            assertThat(d.getArchivoUrl()).isNotBlank();
            assertThat(d.getSubidoPor().getId()).isEqualTo(col.getId());
            assertThat(d.isActivo()).isTrue();
        });
    }

    @Test
    void alSubirUnDocumentoSeNotificaAlPmDelProyecto() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA, null);

        documentoService.subirDocumento(col, proyecto.getId(), archivo("informe.pdf", "application/pdf", PDF));

        List<Notificacion> delPm = notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(pm);
        assertThat(delPm).hasSize(1);
        assertThat(delPm.get(0).getTipo()).isEqualTo("DOCUMENTO_SUBIDO");
        assertThat(delPm.get(0).getDescripcion()).contains("informe.pdf").contains(proyecto.getNombre());
    }

    @Test
    void soloSePuedeSubirConUnaAsignacionActiva() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario sinAsignacion = usuario("sin", "COLABORADOR");
        Usuario finalizado = usuario("fin", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(finalizado, proyecto, EstadoAsignacion.FINALIZADA, MotivoFinalizacion.OTRO);

        assertThatThrownBy(() -> documentoService.subirDocumento(sinAsignacion, proyecto.getId(),
                archivo("informe.pdf", "application/pdf", PDF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes una asignación activa en este proyecto.");

        // Un proyecto finalizado deja de recibir archivos nuevos aunque el colaborador conserve acceso de lectura.
        assertThatThrownBy(() -> documentoService.subirDocumento(finalizado, proyecto.getId(),
                archivo("informe.pdf", "application/pdf", PDF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes una asignación activa en este proyecto.");

        assertThatThrownBy(() -> documentoService.subirDocumento(sinAsignacion, 999_999_999L,
                archivo("informe.pdf", "application/pdf", PDF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El proyecto no existe.");
    }

    @Test
    void validarArchivoDevuelveElMensajeDeCadaRegla() {
        assertThat(documentoService.validarArchivo(null)).isEqualTo("Selecciona un archivo para subir.");
        assertThat(documentoService.validarArchivo(archivo("vacio.pdf", "application/pdf", new byte[0])))
                .isEqualTo("Selecciona un archivo para subir.");
        assertThat(documentoService.validarArchivo(archivo("", "application/pdf", PDF)))
                .isEqualTo("El archivo no tiene un nombre válido.");
        assertThat(documentoService.validarArchivo(archivo("sinextension", "application/pdf", PDF)))
                .isEqualTo("El archivo debe tener una extensión válida.");
        assertThat(documentoService.validarArchivo(archivo("termina.", "application/pdf", PDF)))
                .isEqualTo("El archivo debe tener una extensión válida.");
        assertThat(documentoService.validarArchivo(archivo("virus.exe", "application/octet-stream", new byte[]{1})))
                .isEqualTo("Tipo de archivo no permitido. Formatos aceptados: PDF, Word, Excel, imágenes y archivos de diseño.");
        assertThat(documentoService.validarArchivo(archivo("enorme.pdf", "application/pdf", new byte[20 * 1024 * 1024 + 1])))
                .isEqualTo("El archivo no puede superar los 20 MB.");
        assertThat(documentoService.validarArchivo(archivo("bien.PDF", "application/pdf", PDF))).isNull();
    }

    @Test
    void unArchivoInvalidoNoCreaNingunDocumento() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA, null);

        assertThatThrownBy(() -> documentoService.subirDocumento(col, proyecto.getId(),
                archivo("virus.exe", "application/octet-stream", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tipo de archivo no permitido");

        assertThat(documentoService.listarDocumentos(col, proyecto.getId(), null, null)).isEmpty();
    }

    @Test
    void elListadoSeFiltraPorCategoriaYPorTextoSinDistinguirMayusculas() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA, null);
        documentoService.subirDocumento(col, proyecto.getId(), archivo("informe-final.pdf", "application/pdf", PDF));
        documentoService.subirDocumento(col, proyecto.getId(), archivo("presupuesto.xlsx", "application/octet-stream", new byte[]{1, 2, 3}));

        assertThat(documentoService.listarDocumentos(col, proyecto.getId(), "PDF", null))
                .extracting(Documento::getNombre).containsExactly("informe-final.pdf");
        assertThat(documentoService.listarDocumentos(col, proyecto.getId(), "EXCEL", null))
                .extracting(Documento::getNombre).containsExactly("presupuesto.xlsx");
        assertThat(documentoService.listarDocumentos(col, proyecto.getId(), null, "PRESUP"))
                .extracting(Documento::getNombre).containsExactly("presupuesto.xlsx");
        assertThat(documentoService.listarDocumentos(col, proyecto.getId(), "", "  ")).hasSize(2);
        assertThat(documentoService.listarDocumentos(col, proyecto.getId(), "WORD", null)).isEmpty();
        assertThat(documentoService.listarDocumentos(col, proyecto.getId(), null, "zzz")).isEmpty();
    }

    @Test
    void elAccesoDeLecturaExigeAsignacionActivaOFinalizadaSinBajoDesempeno() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario activo = usuario("act", "COLABORADOR");
        Usuario finalizadoNormal = usuario("fin", "COLABORADOR");
        Usuario finalizadoPorDesempeno = usuario("bad", "COLABORADOR");
        Usuario ajeno = usuario("ajeno", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(activo, proyecto, EstadoAsignacion.ACTIVA, null);
        asignar(finalizadoNormal, proyecto, EstadoAsignacion.FINALIZADA, MotivoFinalizacion.PROYECTO_CANCELADO);
        asignar(finalizadoPorDesempeno, proyecto, EstadoAsignacion.FINALIZADA, MotivoFinalizacion.BAJO_DESEMPENO);

        assertThat(documentoService.listarDocumentos(activo, proyecto.getId(), null, null)).isEmpty();
        assertThat(documentoService.listarDocumentos(finalizadoNormal, proyecto.getId(), null, null)).isEmpty();

        assertThatThrownBy(() -> documentoService.listarDocumentos(finalizadoPorDesempeno, proyecto.getId(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a los documentos de este proyecto.");
        assertThatThrownBy(() -> documentoService.listarDocumentos(ajeno, proyecto.getId(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a los documentos de este proyecto.");
        assertThatThrownBy(() -> documentoService.listarDocumentos(activo, 999_999_999L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El proyecto no existe.");
    }

    @Test
    void soloQuienSubioElDocumentoOUnAdministradorPuedeEliminarlo() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario autor = usuario("autor", "COLABORADOR");
        Usuario companero = usuario("comp", "COLABORADOR");
        Usuario admin = usuario("admin", "ADMINISTRADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(autor, proyecto, EstadoAsignacion.ACTIVA, null);
        asignar(companero, proyecto, EstadoAsignacion.ACTIVA, null);

        documentoService.subirDocumento(autor, proyecto.getId(), archivo("uno.pdf", "application/pdf", PDF));
        documentoService.subirDocumento(autor, proyecto.getId(), archivo("dos.pdf", "application/pdf", PDF));
        List<Documento> subidos = documentoRepository.findByProyectoAndActivoTrueOrderByFechaCreacionDesc(proyecto);
        Long idUno = subidos.stream().filter(d -> d.getNombre().equals("uno.pdf")).findFirst().orElseThrow().getId();
        Long idDos = subidos.stream().filter(d -> d.getNombre().equals("dos.pdf")).findFirst().orElseThrow().getId();

        assertThatThrownBy(() -> documentoService.eliminarDocumento(companero, idUno))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Solo quien subió el documento (o un Administrador) puede eliminarlo.");
        assertThat(documentoRepository.findByIdAndActivoTrue(idUno)).isPresent();

        documentoService.eliminarDocumento(autor, idUno);
        documentoService.eliminarDocumento(admin, idDos);

        // Es una baja lógica: el registro sigue en la base, pero ya no se lista ni se puede volver a eliminar.
        assertThat(documentoRepository.findById(idUno)).get().extracting(Documento::isActivo).isEqualTo(false);
        assertThat(documentoService.listarDocumentos(autor, proyecto.getId(), null, null)).isEmpty();
        assertThatThrownBy(() -> documentoService.eliminarDocumento(autor, idUno))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El documento no existe o ya fue eliminado.");
    }

    // ---------- Utilidades ----------

    private MockMultipartFile archivo(String nombre, String tipo, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, tipo, contenido);
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

    private Proyecto proyecto(Usuario pm) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto docs " + System.nanoTime());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(EstadoProyecto.ACTIVO);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(3);
        p.setPm(pm);
        p.setPresupuesto(new BigDecimal("10000.00"));
        p.setFechaInicio(LocalDate.now().minusMonths(1));
        p.setFechaFinEstimada(LocalDate.now().plusMonths(3));
        return proyectoRepository.save(p);
    }

    private void asignar(Usuario colaborador, Proyecto proyecto, EstadoAsignacion estado, MotivoFinalizacion motivo) {
        Asignacion a = new Asignacion();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setHorasSemanales(new BigDecimal("8"));
        a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        a.setEstado(estado);
        a.setAprobadoPorPm(true);
        a.setAprobadoPorRm(true);
        a.setMotivoFinalizacion(motivo);
        asignacionRepository.save(a);
    }
}