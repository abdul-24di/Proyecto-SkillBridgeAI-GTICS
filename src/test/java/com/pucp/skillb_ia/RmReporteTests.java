package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.RmReporteView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoEntrega;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.rm.RmReporteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class RmReporteTests {
    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private RmReporteService reporteService;

    private MockMvc mockMvc;
    private Proyecto proyecto;
    private Usuario pm;
    private Usuario colaboradorUno;
    private Usuario colaboradorDos;
    private YearMonth periodo;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        periodo = YearMonth.now();
        pm = usuario("pm.reportes@skillbridge.test", "Patricia", "Mora", rol("PROJECT_MANAGER"), null);
        colaboradorUno = usuario("ana.reportes@skillbridge.test", "Ana", "Álvarez", rol("COLABORADOR"), "Backend Developer");
        colaboradorDos = usuario("luis.reportes@skillbridge.test", "Luis", "Ramos", rol("COLABORADOR"), "QA Engineer");

        proyecto = proyectoRepository.findAll().stream()
                .filter(item -> "Reporte mensual test".equals(item.getNombre()))
                .findFirst().orElseGet(Proyecto::new);
        if (proyecto.getId() != null) actividadRepository.deleteAll(actividadRepository.findByProyecto(proyecto));
        proyecto.setNombre("Reporte mensual test");
        proyecto.setDescripcion("Proyecto para verificar los reportes del RM.");
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setPrioridad(Prioridad.ALTA);
        proyecto.setJustificacionPrioridad("Validación automática.");
        proyecto.setPresupuesto(new BigDecimal("12500.00"));
        proyecto.setColaboradoresRequeridos(2);
        proyecto.setPm(pm);
        proyecto = proyectoRepository.save(proyecto);

        actividad("API completada", colaboradorUno, "40.00", EstadoActividad.COMPLETADA,
                periodo.atDay(5).atTime(10, 0));
        actividad("Pruebas completadas", colaboradorUno, "12.00", EstadoActividad.COMPLETADA,
                periodo.atDay(8).atTime(15, 0));
        actividad("Validación completada", colaboradorDos, "8.00", EstadoActividad.COMPLETADA,
                periodo.atDay(10).atTime(9, 0));
        actividad("Actividad del mes anterior", colaboradorUno, "20.00", EstadoActividad.COMPLETADA,
                periodo.minusMonths(1).atEndOfMonth().atTime(12, 0));
        actividad("Actividad aún en revisión", colaboradorDos, "60.00", EstadoActividad.EN_REVISION, null);
    }

    @Test
    void calculaSoloActividadesCompletadasEnElPeriodo() {
        RmReporteView reporte = reporteService.generar(periodo.toString(), proyecto.getId(), null);

        assertEquals(0, new BigDecimal("60.00").compareTo(reporte.getHorasTotales()));
        assertEquals(2, reporte.getColaboradoresInvolucrados());
        assertEquals(2, reporte.getDetalles().size());
        RmReporteView.ColaboradorReporte ana = reporte.getColaboradores().stream()
                .filter(item -> item.getNombre().startsWith("Ana"))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("52.00").compareTo(ana.getHoras()));
        assertEquals(2, ana.getTareas());
        assertEquals(1, reporteService.filtrarColaboradores(reporte, "alvarez").size());
    }

    @Test
    void renderizaAmbasVistasConDatosRealesYSinInformacionSalarial() throws Exception {
        mockMvc.perform(get("/rm/reportes/recursos")
                        .param("periodo", periodo.toString())
                        .param("proyecto", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-reporte-recursos"))
                .andExpect(model().attributeExists("reporte", "periodos", "proyectosFiltro", "estadosFiltro"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reporte mensual test")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("60.00 h")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sueldo_base"))));

        mockMvc.perform(get("/rm/reportes/horas-colaboradores")
                        .param("periodo", periodo.toString())
                        .param("proyecto", proyecto.getId().toString())
                        .param("busqueda", "ana"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-horas-colaboradores"))
                .andExpect(model().attribute("totalColaboradoresHoras", 1))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ana Álvarez")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Luis Ramos"))));
    }

    @Test
    void descargaExcelYPdfValidos() throws Exception {
        MvcResult excel = mockMvc.perform(get("/rm/reportes/recursos/excel")
                        .param("periodo", periodo.toString())
                        .param("proyecto", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();
        byte[] excelBytes = excel.getResponse().getContentAsByteArray();
        assertTrue(excelBytes.length > 1000);
        assertEquals('P', excelBytes[0]);
        assertEquals('K', excelBytes[1]);

        MvcResult pdf = mockMvc.perform(get("/rm/reportes/recursos/pdf")
                        .param("periodo", periodo.toString())
                        .param("proyecto", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                .andExpect(content().contentType("application/pdf"))
                .andReturn();
        assertTrue(pdf.getResponse().getContentAsString().startsWith("%PDF-1.4"));
    }

    private Actividad actividad(String titulo, Usuario colaborador, String horas,
                                EstadoActividad estado, LocalDateTime fechaEntrega) {
        Actividad actividad = new Actividad();
        actividad.setProyecto(proyecto);
        actividad.setColaborador(colaborador);
        actividad.setTitulo(titulo);
        actividad.setDescripcion("Actividad de prueba del reporte.");
        actividad.setHorasEstimadas(new BigDecimal(horas));
        actividad.setFechaLimite(periodo.atEndOfMonth());
        actividad.setEstado(estado);
        actividad.setCreadoPor(pm);
        actividad.setFechaEntrega(fechaEntrega);
        if (estado == EstadoActividad.COMPLETADA) actividad.setEstadoEntrega(EstadoEntrega.A_TIEMPO);
        return actividadRepository.save(actividad);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }

    private Usuario usuario(String correo, String nombre, String apellido, Rol rol, String cargo) {
        Usuario usuario = usuarioRepository.findByCorreo(correo).orElseGet(Usuario::new);
        usuario.setCorreo(correo);
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setCargo(cargo);
        usuario.setSueldoBase(cargo == null ? null : new BigDecimal("9999.00"));
        return usuarioRepository.save(usuario);
    }
}
