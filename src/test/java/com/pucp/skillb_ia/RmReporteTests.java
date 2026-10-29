package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.RmReporteView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoEntrega;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.rm.RmReporteExportService;
import com.pucp.skillb_ia.service.rm.RmReporteService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.pucp.skillb_ia.service.rm.RmReporteExportService.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class RmReporteTests {
    // Distribución del Excel: título, 4 filas de encabezado, métricas y secciones (TASK-037).
    private static final int FILA_CABECERA_PROYECTOS = 12;
    private static final int FILA_CABECERA_DETALLE = 7;
    private static final String EXCEL = "/rm/reportes/recursos/excel";
    private static final String PDF = "/rm/reportes/recursos/pdf";

    @Autowired private WebApplicationContext context;
    @Autowired private RmReporteExportService exportService;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CargoRepository cargoRepository;
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
        if (proyecto.getId() != null) actividadRepository.deleteAll(actividadRepository.findByProyectoAndActivoTrue(proyecto));
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
    void descargaExcelValido() throws Exception {
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

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            assertEquals(2, libro.getNumberOfSheets());
            Sheet proyectos = libro.getSheet("Proyectos");
            Sheet colaboradores = libro.getSheet("Colaboradores");
            assertNotNull(proyectos);
            assertNotNull(colaboradores);

            Row cabecera = proyectos.getRow(FILA_CABECERA_PROYECTOS);
            assertEquals("Proyecto", cabecera.getCell(0).getStringCellValue());
            assertEquals("Presupuesto asignado", cabecera.getCell(3).getStringCellValue());
            assertEquals("Horas trabajadas", cabecera.getCell(4).getStringCellValue());

            Row fila = proyectos.getRow(FILA_CABECERA_PROYECTOS + 1);
            assertEquals("Reporte mensual test", fila.getCell(0).getStringCellValue());
            Cell presupuesto = fila.getCell(3);
            assertEquals(CellType.NUMERIC, presupuesto.getCellType());
            assertEquals(12500.00, presupuesto.getNumericCellValue(), 0.001);
            assertEquals("\"S/\" #,##0.00", presupuesto.getCellStyle().getDataFormatString());
            Cell horas = fila.getCell(4);
            assertEquals(CellType.NUMERIC, horas.getCellType());
            assertEquals(60.00, horas.getNumericCellValue(), 0.001);
            assertEquals("#,##0.00 \"h\"", horas.getCellStyle().getDataFormatString());

            assertEquals("Colaborador", colaboradores.getRow(FILA_CABECERA_DETALLE).getCell(0).getStringCellValue());
            assertTrue(colaboradores.getLastRowNum() >= FILA_CABECERA_DETALLE + 1);
            assertEquals("Reporte mensual test",
                    colaboradores.getRow(FILA_CABECERA_DETALLE + 1).getCell(2).getStringCellValue());

            for (String formato : libro.getStylesSource().getNumberFormats().values()) {
                assertFalse(formato.startsWith("S/"), "Formato inválido para Excel: " + formato);
            }
        }
    }

    @Test
    void laExportacionPdfYaNoExiste() throws Exception {
        mockMvc.perform(get(PDF).param("periodo", periodo.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void excelTieneElEncabezadoConPeriodoFiltrosFechaYReglaDeHoras() throws Exception {
        String periodoTexto = reporteService.generar(periodo.toString(), null, null).getPeriodoTexto();
        String filtros = "Proyecto: Reporte mensual test | Estado: Activo";
        String hoy = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        try (XSSFWorkbook libro = excel(EXCEL, filtrosProyectoActivo())) {
            for (Sheet hoja : List.of(libro.getSheet("Proyectos"), libro.getSheet("Colaboradores"))) {
                assertEquals(TITULO, texto(hoja, 0, 0), hoja.getSheetName());
                assertEquals(ETIQUETAS_ENCABEZADO, List.of(texto(hoja, 1, 0), texto(hoja, 2, 0),
                        texto(hoja, 3, 0), texto(hoja, 4, 0)));
                assertEquals(periodoTexto, texto(hoja, 1, 1));
                assertEquals(filtros, texto(hoja, 2, 1));
                assertTrue(texto(hoja, 3, 1).startsWith(hoy), texto(hoja, 3, 1));
                assertEquals(RmReporteView.REGLA_HORAS, texto(hoja, 4, 1));
            }
            Sheet colaboradores = libro.getSheet("Colaboradores");
            assertEquals(SECCION_DETALLE, texto(colaboradores, FILA_CABECERA_DETALLE - 1, 0));
            assertEquals(SECCION_PROYECTOS, texto(libro.getSheet("Proyectos"), FILA_CABECERA_PROYECTOS - 1, 0));
        }

        mockMvc.perform(get("/rm/reportes/recursos").params(filtrosProyectoActivo()))
                .andExpect(content().string(containsString("Filtros aplicados: " + filtros)))
                .andExpect(content().string(containsString("Regla de horas trabajadas: " + RmReporteView.REGLA_HORAS)));
    }

    @Test
    void vistaYExcelUsanLasMismasColumnasYEtiquetas() throws Exception {
        String vista = mockMvc.perform(get("/rm/reportes/recursos").params(filtrosProyectoActivo()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(vista.contains(encabezadoTabla(COLUMNAS_PROYECTO) + "<th class=\"text-end\">Acción</th>"), vista);
        assertTrue(vista.contains(encabezadoTabla(COLUMNAS_DETALLE)));
        for (String etiqueta : METRICAS) assertTrue(vista.contains(">" + etiqueta + "<"), etiqueta);
        assertTrue(vista.contains(">" + SECCION_PROYECTOS + "<") && vista.contains(">" + SECCION_DETALLE + "<"));
        assertFalse(vista.contains("<th>Rol</th>"));
        assertFalse(vista.contains("Tareas consideradas"));
        assertTrue(vista.contains("<td class=\"fw-semibold\">Ana Álvarez</td><td>Backend Developer</td><td>Reporte mensual test</td>"));

        try (XSSFWorkbook libro = excel(EXCEL, filtrosProyectoActivo())) {
            assertEquals(COLUMNAS_PROYECTO, fila(libro.getSheet("Proyectos"), FILA_CABECERA_PROYECTOS, 6));
            assertEquals(COLUMNAS_DETALLE, fila(libro.getSheet("Colaboradores"), FILA_CABECERA_DETALLE, 5));
            Sheet proyectos = libro.getSheet("Proyectos");
            for (int indice = 0; indice < METRICAS.size(); indice++) {
                assertEquals(METRICAS.get(indice), texto(proyectos, 6 + indice, 0));
            }
            assertEquals(2.0, proyectos.getRow(FILA_CABECERA_PROYECTOS + 1).getCell(5).getNumericCellValue());
        }
    }

    @Test
    void dineroYHorasTienenFormatoUniformeEnVistaYExcel() throws Exception {
        proyecto.setPresupuesto(new BigDecimal("1234567.50"));
        proyectoRepository.save(proyecto);

        mockMvc.perform(get("/rm/reportes/recursos").params(filtrosProyectoActivo()))
                .andExpect(content().string(containsString("S/ 1,234,567.50")))
                .andExpect(content().string(containsString("60.00 h")))
                .andExpect(content().string(containsString("52.00 h")));

        try (XSSFWorkbook libro = excel(EXCEL, filtrosProyectoActivo())) {
            Sheet proyectos = libro.getSheet("Proyectos");
            Cell presupuesto = proyectos.getRow(FILA_CABECERA_PROYECTOS + 1).getCell(3);
            assertEquals(1234567.50, presupuesto.getNumericCellValue(), 0.001);
            assertEquals("\"S/\" #,##0.00", presupuesto.getCellStyle().getDataFormatString());
            assertEquals("\"S/\" #,##0.00", proyectos.getRow(7).getCell(1).getCellStyle().getDataFormatString());
            assertEquals("#,##0.00 \"h\"", proyectos.getRow(8).getCell(1).getCellStyle().getDataFormatString());
            Cell horas = libro.getSheet("Colaboradores").getRow(FILA_CABECERA_DETALLE + 1).getCell(3);
            assertEquals("#,##0.00 \"h\"", horas.getCellStyle().getDataFormatString());
        }
    }

    @Test
    void presupuestoNuloSeMuestraComoCeroEnTodasLasSalidas() throws Exception {
        proyecto.setPresupuesto(null);
        proyectoRepository.save(proyecto);

        mockMvc.perform(get("/rm/reportes/recursos").params(filtrosProyectoActivo()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<td>S/ 0.00</td>")));
        try (XSSFWorkbook libro = excel(EXCEL, filtrosProyectoActivo())) {
            Cell presupuesto = libro.getSheet("Proyectos").getRow(FILA_CABECERA_PROYECTOS + 1).getCell(3);
            assertEquals(CellType.NUMERIC, presupuesto.getCellType());
            assertEquals(0.0, presupuesto.getNumericCellValue());
            assertEquals("\"S/\" #,##0.00", presupuesto.getCellStyle().getDataFormatString());
        }
    }

    @Test
    void nombreDelArchivoReflejaPeriodoYFiltrosConCaracteresSeguros() throws Exception {
        String base = "reporte-recursos-" + periodo;
        mockMvc.perform(get(EXCEL).param("periodo", periodo.toString()))
                .andExpect(header().string("Content-Disposition", containsString(base + ".xlsx")));
        mockMvc.perform(get(EXCEL).params(filtrosProyectoActivo()))
                .andExpect(header().string("Content-Disposition",
                        containsString(base + "-proyecto-reporte-mensual-test-estado-activo.xlsx")));
        mockMvc.perform(get(EXCEL).param("periodo", periodo.minusMonths(2).toString())
                        .param("hasta", periodo.toString()).param("prioridad", "ALTA"))
                .andExpect(header().string("Content-Disposition", containsString(
                        "reporte-recursos-" + periodo.minusMonths(2) + "-a-" + periodo + "-prioridad-alta.xlsx")));
        mockMvc.perform(get(EXCEL).param("periodo", periodo.toString()).param("estado", "EN_REVISION"))
                .andExpect(header().string("Content-Disposition", containsString(base + "-estado-en-revision.xlsx")));

        assertEquals("reporte-recursos-2026-09-proyecto-analisis-fase-2-nandu-estado-en-revision",
                exportService.nombreArchivo(vistaVacia(7L, "Análisis / Fase #2 “Ñandú”", "EN_REVISION")));
        assertEquals("reporte-recursos-2026-09-proyecto-7",
                exportService.nombreArchivo(vistaVacia(7L, "データ", "")));
        assertEquals("reporte-recursos-2026-09-proyecto-7",
                exportService.nombreArchivo(vistaVacia(7L, null, "")));
        String largo = exportService.nombreArchivo(vistaVacia(7L, "a".repeat(30) + " " + "b".repeat(30), ""));
        assertTrue(largo.matches("[a-z0-9-]+") && largo.length() <= "reporte-recursos-2026-09-proyecto-".length() + 40, largo);
    }

    @Test
    void modalDeExportacionMuestraAlcanceFormatosYConservaLosFiltrosAplicados() throws Exception {
        String vista = mockMvc.perform(get("/rm/reportes/recursos").params(filtrosProyectoActivo()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("archivoExportacion",
                        "reporte-recursos-" + periodo + "-proyecto-reporte-mensual-test-estado-activo"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(vista.contains("data-bs-target=\"#exportReportModal\""));
        // Mismo estilo que los demás modales RM: cabecera con el degradado de .rm-modal (rm-common.css).
        assertTrue(vista.contains("class=\"modal modal-blur fade rm-modal\" id=\"exportReportModal\""));
        assertTrue(vista.contains("class=\"rm-modal-context export-scope\""));
        assertFalse(vista.contains("href=\"/rm/reportes/recursos/excel"), "Ya no hay descarga directa sin modal");
        assertFalse(vista.contains("/rm/reportes/recursos/pdf"));
        assertFalse(vista.contains("Descargar PDF"));
        String modal = vista.substring(vista.indexOf("id=\"exportReportModal\""));
        assertTrue(modal.contains("data-applied-periodo=\"" + periodo + "\""));
        assertTrue(modal.contains("data-applied-proyecto=\"" + proyecto.getId() + "\""));
        assertTrue(modal.contains("data-applied-estado=\"ACTIVO\""));
        assertTrue(modal.contains("<form class=\"modal-content\" id=\"exportReportForm\" method=\"get\""));
        assertTrue(modal.contains("action=\"/rm/reportes/recursos/excel\""));
        assertTrue(modal.contains("id=\"exportExcelButton\">Descargar Excel"));
        assertTrue(modal.contains("Proyecto: Reporte mensual test | Estado: Activo"));
        assertTrue(modal.contains(">1 proyecto(s) y 2 registro(s) por colaborador<"));
        assertTrue(modal.contains("Se exportan todos los registros de estos filtros"));
        for (String columna : COLUMNAS_PROYECTO.subList(1, COLUMNAS_PROYECTO.size())) {
            assertTrue(modal.contains(">" + columna + "<"), columna);
        }
        assertTrue(modal.contains("name=\"colProyecto\"") && modal.contains("name=\"colDetalle\""));
        assertTrue(modal.contains("name=\"hojas\" value=\"proyectos\"") && modal.contains("name=\"hojas\" value=\"colaboradores\""));
        assertTrue(modal.contains("name=\"resumen\""));
        assertTrue(modal.contains(">reporte-recursos-" + periodo + "-proyecto-reporte-mensual-test-estado-activo.xlsx<"));
        assertTrue(modal.contains("class=\"alert alert-warning d-none\" id=\"exportUnappliedAlert\""));
        assertTrue(modal.contains("form=\"reportFiltersForm\""));
        assertTrue(vista.contains("id=\"reportFiltersForm\""));
        assertFalse(modal.contains("id=\"exportEmptyNotice\""));

        // Lo que envía el formulario del modal es exactamente el filtro aplicado.
        org.springframework.util.LinkedMultiValueMap<String, String> enviados = camposOcultos(modal);
        assertEquals(List.of(periodo.toString()), enviados.get("periodo"));
        assertEquals(List.of(proyecto.getId().toString()), enviados.get("proyecto"));
        assertEquals(List.of("ACTIVO"), enviados.get("estado"));
        assertEquals(List.of("true"), enviados.get("personalizado"));
        // Con las casillas marcadas por defecto (resumen y las dos hojas) sale el Excel completo.
        enviados.add("resumen", "true");
        enviados.addAll("hojas", List.of("proyectos", "colaboradores"));
        enviados.addAll("colProyecto", List.of("1", "2", "3", "4", "5"));
        enviados.addAll("colDetalle", List.of("1", "2", "3", "4"));
        try (XSSFWorkbook libro = excel(EXCEL, enviados)) {
            assertEquals("Proyecto: Reporte mensual test | Estado: Activo", texto(libro.getSheet("Proyectos"), 2, 1));
            assertEquals(COLUMNAS_PROYECTO, fila(libro.getSheet("Proyectos"), FILA_CABECERA_PROYECTOS, 6));
        }

        String script = mockMvc.perform(get("/js/rm-js/rm-reporte-recursos.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(script.contains("show.bs.modal") && script.contains("exportUnappliedAlert")
                && script.contains("dataset.appliedPeriodo"));
    }

    @Test
    void modalSinRegistrosLoAvisaYMantieneLaDescarga() throws Exception {
        String vista = mockMvc.perform(get("/rm/reportes/recursos")
                        .param("periodo", periodo.toString()).param("proyecto", "999999999"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(vista.contains("id=\"exportEmptyNotice\""));
        assertTrue(vista.contains(">0 proyecto(s) y 0 registro(s) por colaborador<"));
        assertTrue(vista.contains("Proyecto: No encontrado (ID 999999999) | Estado: Todos"));

        try (XSSFWorkbook libro = excel(EXCEL, parametros("periodo", periodo.toString(), "proyecto", "999999999"))) {
            assertEquals(SIN_PROYECTOS, texto(libro.getSheet("Proyectos"), FILA_CABECERA_PROYECTOS + 1, 0));
            assertEquals(SIN_DETALLES, texto(libro.getSheet("Colaboradores"), FILA_CABECERA_DETALLE + 1, 0));
        }
        assertTrue(vista.contains(SIN_PROYECTOS) && vista.contains(SIN_DETALLES));
    }

    @Test
    void endpointsDirectosSiguenFuncionandoSinParametrosYConLosAnteriores() throws Exception {
        String actual = "reporte-recursos-" + YearMonth.now();
        mockMvc.perform(get(EXCEL))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(actual + ".xlsx")));
        mockMvc.perform(get(EXCEL).param("periodo", periodo.toString()).param("proyecto", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void parametrosVaciosOInvalidosNoProducenErroresTecnicos() throws Exception {
        String mesActual = reporteService.generar(null, null, null).getPeriodoTexto();
        List<org.springframework.util.MultiValueMap<String, String>> casos = List.of(
                parametros("periodo", "", "proyecto", "", "estado", ""),
                parametros("periodo", "abc", "proyecto", "abc", "estado", "FOO"),
                parametros("periodo", "2026-13", "proyecto", "-5", "estado", "activo"),
                parametros("periodo", " ", "proyecto", "1.5", "estado", " "),
                parametros("proyecto", "99999999999999999999999"));
        for (org.springframework.util.MultiValueMap<String, String> caso : casos) {
            mockMvc.perform(get("/rm/reportes/recursos").params(caso))
                    .andExpect(status().isOk())
                    .andExpect(view().name("rm/rm-reporte-recursos"));
            try (XSSFWorkbook libro = excel(EXCEL, caso)) {
                assertEquals(mesActual, texto(libro.getSheet("Proyectos"), 1, 1), caso.toString());
                assertEquals("Proyecto: Todos los proyectos | Estado: Todos",
                        texto(libro.getSheet("Colaboradores"), 2, 1), caso.toString());
            }
        }
    }

    @Test
    void exportaTodosLosRegistrosDelFiltroYNoSoloUnaPagina() throws Exception {
        List<RmReporteView.ProyectoReporte> proyectos = new ArrayList<>();
        List<RmReporteView.DetalleColaborador> detalles = new ArrayList<>();
        for (long indice = 1; indice <= 25; indice++) {
            proyectos.add(new RmReporteView.ProyectoReporte(indice, "Proyecto masivo " + indice, "ACTIVO",
                    "Activo", "", "Media", "", new BigDecimal("1000.00"), new BigDecimal("2.00"), 1, 50));
            detalles.add(new RmReporteView.DetalleColaborador(indice, "Colaborador masivo " + indice,
                    "QA Engineer", indice, "Proyecto masivo " + indice, new BigDecimal("2.00"), 1));
        }
        RmReporteView masivo = new RmReporteView(YearMonth.of(2026, 9), "Septiembre 2026", null, "",
                null, null, proyectos, detalles, List.of());

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(exportService.crearExcel(masivo)))) {
            Sheet hojaProyectos = libro.getSheet("Proyectos");
            Sheet hojaDetalle = libro.getSheet("Colaboradores");
            assertEquals("Proyecto masivo 25", texto(hojaProyectos, FILA_CABECERA_PROYECTOS + 25, 0));
            assertEquals("Colaborador masivo 25", texto(hojaDetalle, FILA_CABECERA_DETALLE + 25, 0));
            assertEquals(FILA_CABECERA_DETALLE + 25, hojaDetalle.getLastRowNum());
            assertEquals(25000.0, hojaProyectos.getRow(7).getCell(1).getNumericCellValue());
        }

        // Un parámetro de página en la URL no recorta la exportación (TASK-040 coordinada).
        try (XSSFWorkbook sinPagina = excel(EXCEL, filtrosProyectoActivo());
             XSSFWorkbook conPagina = excel(EXCEL, parametros("periodo", periodo.toString(),
                     "proyecto", proyecto.getId().toString(), "estado", "ACTIVO", "pagina", "2",
                     "paginaProyectos", "2", "paginaColaboradores", "2"))) {
            assertEquals(sinPagina.getSheet("Colaboradores").getLastRowNum(),
                    conPagina.getSheet("Colaboradores").getLastRowNum());
            assertEquals(FILA_CABECERA_DETALLE + 2, conPagina.getSheet("Colaboradores").getLastRowNum());
        }
    }

    // ---- Rango de meses, prioridad y contenido elegido del Excel. ----

    @Test
    void rangoDeMesesSumaLasHorasDeTodoElRangoYSeNormaliza() throws Exception {
        RmReporteView rango = reporteService.generar(periodo.minusMonths(2).toString(), periodo.toString(),
                proyecto.getId(), null, null);
        // 60 h del mes + 20 h de la actividad del mes anterior.
        assertEquals(0, new BigDecimal("80.00").compareTo(rango.getHorasTotales()));
        assertTrue(rango.esRango());
        assertTrue(rango.getPeriodoTexto().contains(" – "), rango.getPeriodoTexto());
        assertEquals(periodo.toString(), rango.getPeriodoHastaValor());

        // Un rango que termina antes del mes solo cuenta la actividad anterior; "hasta" anterior o
        // inválido = un solo mes; más de 12 meses se recorta.
        RmReporteView anterior = reporteService.generar(periodo.minusMonths(3).toString(),
                periodo.minusMonths(1).toString(), proyecto.getId(), null, null);
        assertEquals(0, new BigDecimal("20.00").compareTo(anterior.getHorasTotales()));
        assertFalse(reporteService.generar(periodo.toString(), periodo.minusMonths(1).toString(), null, null, null).esRango());
        assertFalse(reporteService.generar(periodo.toString(), "abc", null, null, null).esRango());
        // "Hasta" no pasa del mes actual (periodo = mes actual en estas pruebas).
        assertEquals(periodo, reporteService.generar(periodo.minusMonths(2).toString(),
                periodo.plusMonths(3).toString(), null, null, null).getPeriodoHasta());
        assertFalse(reporteService.generar(periodo.toString(), periodo.plusMonths(1).toString(),
                null, null, null).esRango());
        assertEquals(periodo.minusMonths(12).plusMonths(11),
                reporteService.generar(periodo.minusMonths(12).toString(), periodo.plusMonths(6).toString(),
                        null, null, null).getPeriodoHasta());

        String vista = mockMvc.perform(get("/rm/reportes/recursos").param("periodo", periodo.minusMonths(2).toString())
                        .param("hasta", periodo.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(List.of(periodo.toString()), camposOcultos(vista).get("hasta"), "El modal exporta el mismo rango");

        // El formulario envía año y mes por separado; un "hasta" anterior al "desde" queda en un solo mes.
        YearMonth desde = periodo.minusMonths(2);
        String formulario = mockMvc.perform(get("/rm/reportes/recursos")
                        .param("anioDesde", String.valueOf(desde.getYear()))
                        .param("mesDesde", String.format("%02d", desde.getMonthValue()))
                        .param("anioHasta", String.valueOf(periodo.getYear()))
                        .param("mesHasta", String.format("%02d", periodo.getMonthValue())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(List.of(desde.toString()), camposOcultos(formulario).get("periodo"));
        assertEquals(List.of(periodo.toString()), camposOcultos(formulario).get("hasta"));
        assertTrue(formulario.contains("id=\"anioDesde\"") && formulario.contains("id=\"mesHasta\""));
        String invertido = mockMvc.perform(get("/rm/reportes/recursos")
                        .param("anioDesde", String.valueOf(periodo.getYear()))
                        .param("mesDesde", String.format("%02d", periodo.getMonthValue()))
                        .param("anioHasta", String.valueOf(desde.getYear()))
                        .param("mesHasta", String.format("%02d", desde.getMonthValue())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(List.of(""), camposOcultos(invertido).get("hasta"));
        try (XSSFWorkbook libro = excel(EXCEL, parametros("periodo", periodo.minusMonths(2).toString(),
                "hasta", periodo.toString(), "proyecto", proyecto.getId().toString()))) {
            assertEquals(rango.getPeriodoTexto(), texto(libro.getSheet("Proyectos"), 1, 1));
        }
    }

    @Test
    void filtroDePrioridadIncluyeSoloLosProyectosDeEsaPrioridad() throws Exception {
        assertEquals(List.of("Reporte mensual test"), nombresProyectos(
                reporteService.generar(periodo.toString(), null, proyecto.getId(), null, "ALTA")));
        assertTrue(reporteService.generar(periodo.toString(), null, proyecto.getId(), null, "BAJA")
                .getProyectos().isEmpty());
        // Prioridad inválida = Todas, igual que el estado.
        assertEquals(1, reporteService.generar(periodo.toString(), null, proyecto.getId(), null, "FOO")
                .getProyectos().size());

        try (XSSFWorkbook libro = excel(EXCEL, parametros("periodo", periodo.toString(),
                "proyecto", proyecto.getId().toString(), "prioridad", "ALTA"))) {
            assertEquals("Proyecto: Reporte mensual test | Estado: Todos | Prioridad: Alta",
                    texto(libro.getSheet("Proyectos"), 2, 1));
        }
        String vista = mockMvc.perform(get("/rm/reportes/recursos").param("periodo", periodo.toString()).param("prioridad", "ALTA"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(Pattern.compile("<option value=\"ALTA\"[^>]*selected").matcher(vista).find());
        assertTrue(vista.contains("Prioridad: Alta"));
    }

    @Test
    void excelSoloConLasHojasYColumnasElegidas() throws Exception {
        org.springframework.util.LinkedMultiValueMap<String, String> soloColaboradores = parametros(
                "periodo", periodo.toString(), "proyecto", proyecto.getId().toString(),
                "personalizado", "true", "hojas", "colaboradores", "colDetalle", "3");
        try (XSSFWorkbook libro = excel(EXCEL, soloColaboradores)) {
            assertEquals(1, libro.getNumberOfSheets());
            Sheet hoja = libro.getSheet("Colaboradores");
            assertNotNull(hoja);
            // La primera columna siempre va; las demás, solo las marcadas.
            assertEquals(List.of("Colaborador", "Horas trabajadas"), fila(hoja, FILA_CABECERA_DETALLE, 2));
            assertNull(hoja.getRow(FILA_CABECERA_DETALLE).getCell(2));
            assertEquals("#,##0.00 \"h\"", hoja.getRow(FILA_CABECERA_DETALLE + 1).getCell(1)
                    .getCellStyle().getDataFormatString());
        }

        // Sin resumen: la sección de proyectos sube justo después del encabezado.
        try (XSSFWorkbook libro = excel(EXCEL, parametros("periodo", periodo.toString(),
                "proyecto", proyecto.getId().toString(), "personalizado", "true",
                "hojas", "proyectos", "colProyecto", "4", "colProyecto", "99"))) {
            Sheet hoja = libro.getSheet("Proyectos");
            assertNull(libro.getSheet("Colaboradores"));
            assertEquals(SECCION_PROYECTOS, texto(hoja, 6, 0));
            assertEquals(List.of("Proyecto", "Horas trabajadas"), fila(hoja, 7, 2));
            assertEquals(60.0, hoja.getRow(8).getCell(1).getNumericCellValue(), 0.001);
            assertEquals(TITULO, texto(hoja, 0, 0));
        }

        // Sin ninguna hoja no se genera archivo: vuelve con el mensaje de error.
        mockMvc.perform(get(EXCEL).param("periodo", periodo.toString()).param("personalizado", "true")
                        .header("Referer", "/rm/reportes/recursos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Selecciona al menos una hoja para exportar."));
    }

    // ---- Paginación en el servidor (TASK-040). Los datos masivos se revierten al final de cada prueba. ----

    @Test
    @Transactional
    void recursosPaginaProyectosYDetalleEnElServidorConservandoElOrden() throws Exception {
        prepararDatosMasivos();
        List<String> todosProyectos = nombresProyectos(reporteService.generar(PERIODO_MASIVO.toString(), null, "EN_ESPERA"));
        assertTrue(todosProyectos.size() > 10);
        // Fecha de creación descendente: los 12 proyectos de 2099 encabezan el listado.
        assertEquals(nombreProyectoMasivo(12), todosProyectos.get(0));
        assertEquals(nombreProyectoMasivo(1), todosProyectos.get(11));

        for (int pagina = 1; pagina <= 2; pagina++) {
            String vista = vista("/rm/reportes/recursos", filtrosMasivos("paginaProyectos", String.valueOf(pagina)));
            List<String> esperados = todosProyectos.subList((pagina - 1) * 10, Math.min(pagina * 10, todosProyectos.size()));
            assertEquals(esperados, filas(vista, "proyecto-row"), "Proyectos, página " + pagina);
            // El detalle sigue en su página 1 (orden por colaborador y proyecto).
            assertEquals(colaboradoresMasivos(1, 10), filas(vista, "detalle-row"));
        }

        String paginaDos = vista("/rm/reportes/recursos", filtrosMasivos("paginaColaboradores", "2"));
        assertEquals(colaboradoresMasivos(11, 12), filas(paginaDos, "detalle-row"));
        assertEquals(todosProyectos.subList(0, 10), filas(paginaDos, "proyecto-row"));
        assertTrue(paginaDos.contains("Mostrando 11-12 de 12 registros"));
        assertTrue(paginaDos.contains("Mostrando 1-10 de " + todosProyectos.size() + " proyectos"));
    }

    @Test
    @Transactional
    void paginasDeRecursosSonIndependientesYLosEnlacesConservanFiltrosYLaOtraPagina() throws Exception {
        prepararDatosMasivos();
        List<String> todosProyectos = nombresProyectos(reporteService.generar(PERIODO_MASIVO.toString(), null, "EN_ESPERA"));

        String vista = vista("/rm/reportes/recursos",
                filtrosMasivos("paginaProyectos", "2", "paginaColaboradores", "1"));
        assertEquals(todosProyectos.subList(10, Math.min(20, todosProyectos.size())), filas(vista, "proyecto-row"));
        assertEquals(colaboradoresMasivos(1, 10), filas(vista, "detalle-row"));
        String cruzada = vista("/rm/reportes/recursos",
                filtrosMasivos("paginaProyectos", "1", "paginaColaboradores", "2"));
        assertEquals(todosProyectos.subList(0, 10), filas(cruzada, "proyecto-row"));
        assertEquals(colaboradoresMasivos(11, 12), filas(cruzada, "detalle-row"));

        List<MultiValueMap<String, String>> enlacesProyectos = enlaces(vista, "paginationProyectos");
        List<MultiValueMap<String, String>> enlacesDetalle = enlaces(vista, "paginationDetalles");
        assertFalse(enlacesProyectos.isEmpty());
        assertFalse(enlacesDetalle.isEmpty());
        for (MultiValueMap<String, String> enlace : enlacesProyectos) {
            assertEquals(Set.of("periodo", "hasta", "proyecto", "estado", "prioridad", "paginaProyectos", "paginaColaboradores"), enlace.keySet());
            assertEquals(PERIODO_MASIVO.toString(), enlace.getFirst("periodo"));
            assertEquals("EN_ESPERA", enlace.getFirst("estado"));
            assertEquals("1", enlace.getFirst("paginaColaboradores"), "Conserva la página del detalle");
        }
        for (MultiValueMap<String, String> enlace : enlacesDetalle) {
            assertEquals(Set.of("periodo", "hasta", "proyecto", "estado", "prioridad", "paginaProyectos", "paginaColaboradores"), enlace.keySet());
            assertEquals("2", enlace.getFirst("paginaProyectos"), "Conserva la página de proyectos");
        }
        // Anterior (deshabilitado; el servidor normaliza 0 a 1), números y Siguiente del detalle: 0, 1, 2, 2.
        assertEquals(List.of("0", "1", "2", "2"),
                enlacesDetalle.stream().map(enlace -> enlace.getFirst("paginaColaboradores")).toList());

        // Con proyecto seleccionado, el detalle sigue paginando y sus enlaces conservan el proyecto.
        String conProyecto = vista("/rm/reportes/recursos", parametros("periodo", PERIODO_MASIVO.toString(),
                "proyecto", proyectoMasivo.getId().toString(), "estado", "EN_ESPERA", "paginaColaboradores", "2"));
        assertEquals(List.of(nombreProyectoMasivo(1)), filas(conProyecto, "proyecto-row"));
        assertFalse(conProyecto.contains("id=\"paginationProyectos\""), "Una sola página no muestra controles");
        for (MultiValueMap<String, String> enlace : enlaces(conProyecto, "paginationDetalles")) {
            assertEquals(proyectoMasivo.getId().toString(), enlace.getFirst("proyecto"));
            assertEquals("EN_ESPERA", enlace.getFirst("estado"));
            assertEquals(PERIODO_MASIVO.toString(), enlace.getFirst("periodo"));
            assertEquals("1", enlace.getFirst("paginaProyectos"));
        }
    }

    @Test
    @Transactional
    void indicadoresGraficoYModalUsanElConjuntoCompletoNoLaPagina() throws Exception {
        prepararDatosMasivos();
        RmReporteView completo = reporteService.generar(PERIODO_MASIVO.toString(), null, "EN_ESPERA");
        MvcResult resultado = mockMvc.perform(get("/rm/reportes/recursos")
                        .params(filtrosMasivos("paginaProyectos", "2", "paginaColaboradores", "2")))
                .andExpect(status().isOk())
                .andReturn();
        String vista = resultado.getResponse().getContentAsString();
        RmReporteView reporte = (RmReporteView) resultado.getModelAndView().getModel().get("reporte");

        assertEquals(nombresProyectos(completo), nombresProyectos(reporte), "RmReporteView no se recorta");
        assertEquals(12, reporte.getDetalles().size());
        assertEquals(12, reporte.getColaboradoresInvolucrados());
        assertEquals(0, new BigDecimal("78.00").compareTo(reporte.getHorasTotales()));
        assertTrue(vista.contains("<div class=\"metric-number\">" + completo.getTotalProyectos() + "</div>"));
        assertTrue(vista.contains("78.00 h"));
        // El gráfico pinta todos los proyectos, también los que no están en la página visible.
        for (String nombre : nombresProyectos(completo)) {
            assertTrue(vista.contains("<span class=\"text-truncate\">" + nombre + "</span>"), nombre);
        }
        assertEquals(completo.getTotalProyectos(), ocurrencias(vista, "class=\"progress-bar bg-primary\""));

        // El modal informa el total y envía solo los filtros aplicados, sin parámetros de página.
        String modal = vista.substring(vista.indexOf("id=\"exportReportModal\""));
        assertTrue(modal.contains(">" + completo.getTotalProyectos() + " proyecto(s) y 12 registro(s) por colaborador<"));
        assertEquals(Set.of("periodo", "hasta", "proyecto", "estado", "prioridad", "personalizado"), camposOcultos(modal).keySet());
        assertFalse(modal.contains("paginaProyectos") || modal.contains("paginaColaboradores")
                || modal.contains("name=\"pagina"));
        int inicioFormulario = vista.indexOf("id=\"reportFiltersForm\"");
        String formulario = vista.substring(inicioFormulario, vista.indexOf("</form>", inicioFormulario));
        assertFalse(formulario.contains("pagina"), "El formulario de filtros no envía páginas");
    }

    @Test
    @Transactional
    void excelConPaginaDistintaDeLaPrimeraIncluyeTodosLosRegistrosFiltrados() throws Exception {
        prepararDatosMasivos();
        List<String> todosProyectos = nombresProyectos(reporteService.generar(PERIODO_MASIVO.toString(), null, "EN_ESPERA"));
        MultiValueMap<String, String> conPaginas = filtrosMasivos(
                "paginaProyectos", "2", "paginaColaboradores", "2", "pagina", "2");

        try (XSSFWorkbook libro = excel(EXCEL, conPaginas)) {
            Sheet hojaProyectos = libro.getSheet("Proyectos");
            for (int indice = 0; indice < todosProyectos.size(); indice++) {
                assertEquals(todosProyectos.get(indice), texto(hojaProyectos, FILA_CABECERA_PROYECTOS + 1 + indice, 0));
            }
            Sheet hojaDetalle = libro.getSheet("Colaboradores");
            assertEquals(FILA_CABECERA_DETALLE + 12, hojaDetalle.getLastRowNum());
            List<String> esperados = colaboradoresMasivos(1, 12);
            for (int indice = 0; indice < esperados.size(); indice++) {
                assertEquals(esperados.get(indice), texto(hojaDetalle, FILA_CABECERA_DETALLE + 1 + indice, 0));
            }
        }
    }

    @Test
    @Transactional
    void horasPaginaEnElServidorConservandoOrdenYFiltros() throws Exception {
        prepararDatosMasivos();
        // Orden actual: horas descendentes (12 h, 11 h, ...), distinto del orden por nombre.
        MvcResult primera = mockMvc.perform(get("/rm/reportes/horas-colaboradores")
                        .params(filtrosHoras("pagina", "1")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalColaboradoresHoras", 12))
                .andExpect(model().attribute("paginaActual", 1))
                .andExpect(model().attribute("totalPaginas", 2))
                .andReturn();
        String vistaUno = primera.getResponse().getContentAsString();
        assertEquals(colaboradoresMasivosPorHoras(12, 3), filas(vistaUno, "colaborador-row"));
        assertTrue(vistaUno.contains("Mostrando 1-10 de 12 colaboradores"));

        String colaboradorId = colaboradorMasivo(1).getId().toString();
        MvcResult segunda = mockMvc.perform(get("/rm/reportes/horas-colaboradores")
                        .params(filtrosHoras("pagina", "2", "colaborador", colaboradorId)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalColaboradoresHoras", 12))
                .andExpect(model().attribute("enReferencia", 0L))
                .andExpect(model().attribute("debajoReferencia", 12L))
                .andReturn();
        String vistaDos = segunda.getResponse().getContentAsString();
        assertEquals(colaboradoresMasivosPorHoras(2, 1), filas(vistaDos, "colaborador-row"));
        assertEquals(0, new BigDecimal("78.00").compareTo(
                (BigDecimal) segunda.getModelAndView().getModel().get("totalHorasColaboradores")));
        RmReporteView.ColaboradorReporte seleccionado = (RmReporteView.ColaboradorReporte)
                segunda.getModelAndView().getModel().get("colaboradorSeleccionado");
        assertEquals(nombreColaboradorMasivo(1), seleccionado.getNombre());

        List<MultiValueMap<String, String>> enlaces = enlaces(vistaDos, "pagination");
        assertEquals(List.of("1", "1", "2", "3"), enlaces.stream().map(enlace -> enlace.getFirst("pagina")).toList());
        for (MultiValueMap<String, String> enlace : enlaces) {
            assertEquals(Set.of("periodo", "proyecto", "busqueda", "colaborador", "pagina"), enlace.keySet());
            assertEquals(PERIODO_MASIVO.toString(), enlace.getFirst("periodo"));
            assertEquals(proyectoMasivo.getId().toString(), enlace.getFirst("proyecto"));
            assertEquals("pag", enlace.getFirst("busqueda"));
            assertEquals(colaboradorId, enlace.getFirst("colaborador"));
        }
        assertTrue(Pattern.compile("<li class=\"page-item disabled\">\\s*<a class=\"page-link\"[^>]*>Siguiente</a>")
                .matcher(bloqueLista(vistaDos, "pagination")).find(), "Siguiente deshabilitado en la última página");
        // "Ver desglose" mantiene la página visible.
        Matcher desglose = Pattern.compile("href=\"([^\"]*colaborador=[^\"]*)\">Ver desglose").matcher(vistaDos);
        assertTrue(desglose.find());
        assertEquals("2", parametrosEnlace(desglose.group(1)).getFirst("pagina"));
    }

    @Test
    @Transactional
    void losFiltrosSeAplicanAntesDePaginar() throws Exception {
        prepararDatosMasivos();
        // "tester" excluye al colaborador 12 (Analista): quedan 11, en 2 páginas, con el mismo orden.
        String vista = vista("/rm/reportes/horas-colaboradores", filtrosHoras("busqueda", "tester", "pagina", "2"));
        assertEquals(List.of(nombreColaboradorMasivo(1)), filas(vista, "colaborador-row"));
        assertTrue(vista.contains("Mostrando 11-11 de 11 colaboradores"));
        String primera = vista("/rm/reportes/horas-colaboradores", filtrosHoras("busqueda", "tester"));
        assertEquals(colaboradoresMasivosPorHoras(11, 2), filas(primera, "colaborador-row"));

        // Recursos: el estado filtra antes de paginar; con ACTIVO no aparece ningún proyecto masivo.
        String activos = vista("/rm/reportes/recursos", parametros("periodo", PERIODO_MASIVO.toString(),
                "estado", "ACTIVO", "paginaProyectos", "1"));
        assertTrue(filas(activos, "proyecto-row").stream().noneMatch(nombre -> nombre.startsWith("Pag proyecto")));
        assertTrue(filas(activos, "detalle-row").isEmpty(), "Sin actividades de proyectos activos en el periodo");
        // Con proyecto, el detalle solo contiene ese proyecto antes de paginar (12 registros en 2 páginas).
        String conProyecto = vista("/rm/reportes/recursos", parametros("periodo", PERIODO_MASIVO.toString(),
                "proyecto", proyectoMasivo.getId().toString()));
        assertTrue(conProyecto.contains("Mostrando 1-10 de 12 registros"));
        assertTrue(conProyecto.contains("Mostrando 1-1 de 1 proyectos"));
    }

    @ParameterizedTest(name = "página \"{0}\" → {1}")
    @CsvSource({"'', primera", "'  ', primera", "abc, primera", "1.5, primera", "0, primera",
            "-3, primera", "999, ultima", "99999999999, ultima"})
    @Transactional
    void paginasInvalidasOExcesivasNoProducenErroresTecnicos(String pagina, String esperada) throws Exception {
        prepararDatosMasivos();
        boolean ultima = "ultima".equals(esperada);
        MvcResult recursos = mockMvc.perform(get("/rm/reportes/recursos")
                        .params(filtrosMasivos("paginaProyectos", pagina, "paginaColaboradores", pagina)))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-reporte-recursos"))
                .andReturn();
        var proyectos = (RmReporteService.PaginaReporte<?>) recursos.getModelAndView().getModel().get("paginaProyectos");
        var detalles = (RmReporteService.PaginaReporte<?>) recursos.getModelAndView().getModel().get("paginaColaboradores");
        assertEquals(ultima ? proyectos.totalPaginas() : 1, proyectos.paginaActual());
        assertEquals(ultima ? 2 : 1, detalles.paginaActual());
        assertEquals(ultima ? colaboradoresMasivos(11, 12) : colaboradoresMasivos(1, 10),
                filas(recursos.getResponse().getContentAsString(), "detalle-row"));

        String horas = mockMvc.perform(get("/rm/reportes/horas-colaboradores").params(filtrosHoras("pagina", pagina)))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-horas-colaboradores"))
                .andExpect(model().attribute("paginaActual", ultima ? 2 : 1))
                .andReturn().getResponse().getContentAsString();
        assertEquals(ultima ? colaboradoresMasivosPorHoras(2, 1) : colaboradoresMasivosPorHoras(12, 3),
                filas(horas, "colaborador-row"));
    }

    @Test
    void sinRegistrosMuestraUnaPaginaValidaVacia() throws Exception {
        String recursos = vista("/rm/reportes/recursos", parametros("periodo", "1999-01",
                "proyecto", "999999999", "paginaProyectos", "3", "paginaColaboradores", "-1"));
        assertTrue(recursos.contains("Mostrando 0-0 de 0 proyectos"));
        assertTrue(recursos.contains("Mostrando 0-0 de 0 registros"));
        assertTrue(recursos.contains(SIN_PROYECTOS) && recursos.contains(SIN_DETALLES));
        assertFalse(recursos.contains("class=\"page-link\""), "Sin registros no hay controles de página");

        mockMvc.perform(get("/rm/reportes/horas-colaboradores")
                        .param("periodo", "1999-01").param("busqueda", "zzz-sin-coincidencias").param("pagina", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 1))
                .andExpect(model().attribute("totalPaginas", 1))
                .andExpect(model().attribute("totalRegistros", 0L))
                .andExpect(content().string(containsString("Mostrando 0-0 de 0 colaboradores")))
                .andExpect(content().string(containsString("No hay colaboradores que coincidan con los filtros.")));
    }

    @Test
    @Transactional
    void listadosFuncionanSinJavaScriptYSinEnlacesVacios() throws Exception {
        prepararDatosMasivos();
        String recursos = vista("/rm/reportes/recursos", filtrosMasivos("paginaProyectos", "2"));
        String horas = vista("/rm/reportes/horas-colaboradores", filtrosHoras("pagina", "2"));
        assertFalse(recursos.contains("-row d-none") || horas.contains("-row d-none"), "Las filas no dependen de JS para ocultarse");
        // Los controles son enlaces GET reales con Anterior, números y Siguiente (ninguno con href="#").
        for (String bloque : List.of(bloqueLista(recursos, "paginationProyectos"),
                bloqueLista(recursos, "paginationDetalles"), bloqueLista(horas, "pagination"))) {
            assertTrue(bloque.contains(">Anterior</a>") && bloque.contains(">1</a>")
                    && bloque.contains(">2</a>") && bloque.contains(">Siguiente</a>"), bloque);
            assertFalse(bloque.contains("href=\"#\""), "Ningún control de página usa href=\"#\"");
            assertEquals(ocurrencias(bloque, "class=\"page-link\""),
                    ocurrencias(bloque, "href=\"/rm/reportes/"), "Cada control es un enlace GET al reporte");
        }

        assertFalse(horas.contains("rm-horas-colaboradores.js"), "La vista de horas ya no carga JS propio");
        // Se comprueba la fuente: target/classes puede conservar la copia anterior sin "clean".
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/resources/static/js/rm-js/rm-horas-colaboradores.js")), "El archivo se eliminó");
        String script = mockMvc.perform(get("/js/rm-js/rm-reporte-recursos.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(script.contains("setupPagination") || script.contains("pageSize")
                || script.contains("paginationProyectos") || script.contains("page-link"), "Sin paginación en JS");
        assertTrue(script.contains("exportUnappliedAlert"), "Conserva el comportamiento del modal (TASK-037)");
    }

    private static final YearMonth PERIODO_MASIVO = YearMonth.of(2001, 3);
    private Proyecto proyectoMasivo;
    private final List<Usuario> colaboradoresMasivos = new ArrayList<>();

    /**
     * 12 proyectos EN_ESPERA creados "en 2099" (encabezan el orden por fecha de creación) y 12
     * colaboradores con una tarea completada en el primero: el colaborador i registra i horas.
     * El 12 es "Analista"; los demás, "Tester".
     */
    private void prepararDatosMasivos() {
        for (int indice = 1; indice <= 12; indice++) {
            Proyecto nuevo = new Proyecto();
            nuevo.setNombre(nombreProyectoMasivo(indice));
            nuevo.setDescripcion("Proyecto para verificar la paginación del reporte.");
            nuevo.setEstado(EstadoProyecto.EN_ESPERA);
            nuevo.setPrioridad(Prioridad.MEDIA);
            nuevo.setJustificacionPrioridad("Validación automática.");
            nuevo.setPresupuesto(new BigDecimal("1000.00"));
            nuevo.setColaboradoresRequeridos(1);
            nuevo.setPm(pm);
            nuevo.setFechaCreacion(LocalDateTime.of(2099, 1, 1, 0, 0).plusMinutes(indice));
            nuevo = proyectoRepository.save(nuevo);
            if (indice == 1) proyectoMasivo = nuevo;
        }
        colaboradoresMasivos.clear();
        for (int indice = 1; indice <= 12; indice++) {
            Usuario colaborador = usuario(String.format("pag%02d.reportes@skillbridge.test", indice),
                    "Colaborador", String.format("Pag %02d", indice), rol("COLABORADOR"),
                    indice == 12 ? "Analista Paginado" : "Tester Paginado");
            colaboradoresMasivos.add(colaborador);
            Actividad actividad = new Actividad();
            actividad.setProyecto(proyectoMasivo);
            actividad.setColaborador(colaborador);
            actividad.setTitulo("Tarea masiva " + indice);
            actividad.setDescripcion("Actividad de prueba de la paginación.");
            actividad.setHorasEstimadas(BigDecimal.valueOf(indice).setScale(2));
            actividad.setFechaLimite(PERIODO_MASIVO.atEndOfMonth());
            actividad.setEstado(EstadoActividad.COMPLETADA);
            actividad.setEstadoEntrega(EstadoEntrega.A_TIEMPO);
            actividad.setCreadoPor(pm);
            actividad.setFechaEntrega(PERIODO_MASIVO.atDay(10).atTime(9, 0));
            actividadRepository.save(actividad);
        }
    }

    private String nombreProyectoMasivo(int indice) {
        return String.format("Pag proyecto %02d", indice);
    }

    private String nombreColaboradorMasivo(int indice) {
        return String.format("Colaborador Pag %02d", indice);
    }

    private Usuario colaboradorMasivo(int indice) {
        return colaboradoresMasivos.get(indice - 1);
    }

    /** Nombres de los colaboradores {@code desde..hasta} en orden ascendente (orden del detalle). */
    private List<String> colaboradoresMasivos(int desde, int hasta) {
        List<String> nombres = new ArrayList<>();
        for (int indice = desde; indice <= hasta; indice++) nombres.add(nombreColaboradorMasivo(indice));
        return nombres;
    }

    /** Nombres en orden descendente de horas (orden del consolidado de horas). */
    private List<String> colaboradoresMasivosPorHoras(int desde, int hasta) {
        List<String> nombres = new ArrayList<>();
        for (int indice = desde; indice >= hasta; indice--) nombres.add(nombreColaboradorMasivo(indice));
        return nombres;
    }

    private List<String> nombresProyectos(RmReporteView reporte) {
        return reporte.getProyectos().stream().map(RmReporteView.ProyectoReporte::getNombre).toList();
    }

    private MultiValueMap<String, String> filtrosMasivos(String... extras) {
        MultiValueMap<String, String> mapa = parametros("periodo", PERIODO_MASIVO.toString(), "proyecto", "", "estado", "EN_ESPERA");
        mapa.addAll(parametros(extras));
        return mapa;
    }

    private MultiValueMap<String, String> filtrosHoras(String... extras) {
        MultiValueMap<String, String> mapa = parametros("periodo", PERIODO_MASIVO.toString(),
                "proyecto", proyectoMasivo.getId().toString(), "busqueda", "pag");
        parametros(extras).forEach((clave, valores) -> mapa.put(clave, valores));
        return mapa;
    }

    private String vista(String ruta, MultiValueMap<String, String> parametros) throws Exception {
        return mockMvc.perform(get(ruta).params(parametros))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Primera celda (nombre) de cada fila renderizada con la clase indicada. */
    private List<String> filas(String html, String clase) {
        Matcher matcher = Pattern.compile("<tr class=\"" + clase + "\">\\s*<td class=\"fw-semibold\">([^<]*)</td>").matcher(html);
        List<String> nombres = new ArrayList<>();
        while (matcher.find()) nombres.add(matcher.group(1));
        return nombres;
    }

    private String bloqueLista(String html, String id) {
        int inicio = html.indexOf("id=\"" + id + "\"");
        assertTrue(inicio >= 0, "Falta #" + id);
        return html.substring(inicio, html.indexOf("</ul>", inicio));
    }

    private List<MultiValueMap<String, String>> enlaces(String html, String id) {
        if (!html.contains("id=\"" + id + "\"")) return List.of();
        Matcher matcher = Pattern.compile("href=\"([^\"]*)\"").matcher(bloqueLista(html, id));
        List<MultiValueMap<String, String>> enlaces = new ArrayList<>();
        while (matcher.find()) enlaces.add(parametrosEnlace(matcher.group(1)));
        return enlaces;
    }

    private MultiValueMap<String, String> parametrosEnlace(String href) {
        return UriComponentsBuilder.fromUriString(href.replace("&amp;", "&")).build().getQueryParams();
    }

    private int ocurrencias(String texto, String fragmento) {
        int total = 0;
        for (int posicion = texto.indexOf(fragmento); posicion >= 0; posicion = texto.indexOf(fragmento, posicion + 1)) total++;
        return total;
    }

    private org.springframework.util.MultiValueMap<String, String> filtrosProyectoActivo() {
        return parametros("periodo", periodo.toString(), "proyecto", proyecto.getId().toString(), "estado", "ACTIVO");
    }

    private org.springframework.util.LinkedMultiValueMap<String, String> parametros(String... pares) {
        org.springframework.util.LinkedMultiValueMap<String, String> mapa = new org.springframework.util.LinkedMultiValueMap<>();
        for (int indice = 0; indice < pares.length; indice += 2) mapa.add(pares[indice], pares[indice + 1]);
        return mapa;
    }

    private org.springframework.util.LinkedMultiValueMap<String, String> camposOcultos(String html) {
        org.springframework.util.LinkedMultiValueMap<String, String> campos = new org.springframework.util.LinkedMultiValueMap<>();
        Matcher matcher = Pattern.compile("<input type=\"hidden\" name=\"([^\"]+)\" value=\"([^\"]*)\">").matcher(html);
        while (matcher.find()) campos.add(matcher.group(1), matcher.group(2));
        return campos;
    }

    private XSSFWorkbook excel(String ruta, org.springframework.util.MultiValueMap<String, String> filtros) throws Exception {
        byte[] contenido = mockMvc.perform(get(ruta).params(filtros))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido));
        for (String formato : libro.getStylesSource().getNumberFormats().values()) {
            assertFalse(formato.startsWith("S/"), "Formato inválido para Excel: " + formato);
        }
        return libro;
    }

    private String texto(Sheet hoja, int fila, int columna) {
        return hoja.getRow(fila).getCell(columna).getStringCellValue();
    }

    private List<String> fila(Sheet hoja, int fila, int columnas) {
        List<String> valores = new ArrayList<>();
        for (int indice = 0; indice < columnas; indice++) valores.add(texto(hoja, fila, indice));
        return valores;
    }

    private String encabezadoTabla(List<String> columnas) {
        StringBuilder html = new StringBuilder();
        columnas.forEach(columna -> html.append("<th>").append(columna).append("</th>"));
        return html.toString();
    }

    private void assertEnOrden(String linea, List<String> partes) {
        int desde = 0;
        for (String parte : partes) {
            int posicion = linea.indexOf(parte, desde);
            assertTrue(posicion >= desde, "Falta '" + parte + "' en orden: " + linea);
            desde = posicion + parte.length();
        }
    }

    private RmReporteView vistaVacia(Long proyectoId, String proyectoNombre, String estado) {
        return new RmReporteView(YearMonth.of(2026, 9), "Septiembre 2026", proyectoId, estado,
                proyectoNombre, null, List.of(), List.of(), List.of());
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
        if (cargo != null) usuario.setCargo(cargoDePrueba(cargo));
        usuario.setSueldoBase(cargo == null ? null : new BigDecimal("9999.00"));
        return usuarioRepository.save(usuario);
    }

    private Cargo cargoDePrueba(String nombre) {
        return cargoRepository.findByNombre(nombre).orElseGet(() -> cargoRepository.save(
                new Cargo(nombre, new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000"))));
    }

    // TASK-031: sin filas, las tablas y el gráfico usan el estado vacío común; filtros, paginación e indicadores siguen.
    @Test
    void sinFilasLosReportesUsanElEstadoVacioComunYConservanFiltrosPaginacionEIndicadores() throws Exception {
        String recursos = vista("/rm/reportes/recursos", parametros("periodo", "1999-01", "proyecto", "999999999"));
        assertEquals(3, ocurrencias(recursos, "class=\"empty-state\""));
        assertTrue(recursos.contains("<tr><td colspan=\"7\" class=\"empty-state-cell\"><div class=\"empty-state\">"), recursos);
        assertTrue(recursos.contains("<tr><td colspan=\"5\" class=\"empty-state-cell\"><div class=\"empty-state\">"));
        assertTrue(recursos.contains("<div class=\"empty-state-title\">Sin proyectos</div>"));
        assertTrue(recursos.contains("<div class=\"empty-state-title\">Sin información para graficar</div>"));
        assertTrue(recursos.contains("<div class=\"empty-state-title\">Sin tareas completadas</div>"));
        assertTrue(recursos.contains(SIN_PROYECTOS) && recursos.contains(SIN_DETALLES));
        assertFalse(recursos.contains("empty-state-action"));
        assertTrue(recursos.contains("id=\"reportFiltersForm\""));
        assertTrue(recursos.contains("Mostrando 0-0 de 0 proyectos") && recursos.contains("Mostrando 0-0 de 0 registros"));
        assertEquals(4, ocurrencias(recursos, "class=\"card metric-card\""));

        String horas = vista("/rm/reportes/horas-colaboradores",
                parametros("periodo", "1999-01", "busqueda", "zzz-sin-coincidencias"));
        assertEquals(1, ocurrencias(horas, "class=\"empty-state\""));
        assertTrue(horas.contains("<tr><td colspan=\"6\" class=\"empty-state-cell\"><div class=\"empty-state\">"), horas);
        assertTrue(horas.contains("<div class=\"empty-state-title\">Sin colaboradores</div>"));
        assertTrue(horas.contains("No hay colaboradores que coincidan con los filtros."));
        assertFalse(horas.contains("empty-state-action"));
        assertTrue(horas.contains("value=\"zzz-sin-coincidencias\""));
        assertTrue(horas.contains("Mostrando 0-0 de 0 colaboradores"));
        assertEquals(4, ocurrencias(horas, "class=\"card metric-card\""));
    }
}
