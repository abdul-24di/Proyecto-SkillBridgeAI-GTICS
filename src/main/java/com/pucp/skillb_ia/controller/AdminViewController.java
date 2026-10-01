package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.AdminAuditoriaService;
import com.pucp.skillb_ia.service.AdminConfiguracionService;
import com.pucp.skillb_ia.service.AdminHabilidadService;
import com.pucp.skillb_ia.service.AdminPerfilService;
import com.pucp.skillb_ia.service.AdminUsuarioService;
import com.pucp.skillb_ia.service.admin.AdminCargoService;
import com.pucp.skillb_ia.service.AdminCursoService;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.ExperienciaProfesional;
import com.pucp.skillb_ia.model.Usuario;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminViewController {

    private final AdminUsuarioService adminUsuarioService;
    private final AdminHabilidadService adminHabilidadService;
    private final AdminCargoService adminCargoService;
    private final AdminConfiguracionService adminConfiguracionService;
    private final AdminAuditoriaService adminAuditoriaService;
    private final AdminPerfilService adminPerfilService;
    private final AdminCursoService adminCursoService;
    private final ColaboradorPerfilService colaboradorPerfilService;

    public AdminViewController(AdminUsuarioService adminUsuarioService, AdminHabilidadService adminHabilidadService,
                                AdminConfiguracionService adminConfiguracionService,
                                AdminAuditoriaService adminAuditoriaService,
                                AdminPerfilService adminPerfilService,
                                AdminCargoService adminCargoService,
                                AdminCursoService adminCursoService,
                                ColaboradorPerfilService colaboradorPerfilService) {
        this.adminUsuarioService = adminUsuarioService;
        this.adminHabilidadService = adminHabilidadService;
        this.adminConfiguracionService = adminConfiguracionService;
        this.adminAuditoriaService = adminAuditoriaService;
        this.adminCargoService = adminCargoService;
        this.adminPerfilService = adminPerfilService;
        this.adminCursoService = adminCursoService;
        this.colaboradorPerfilService = colaboradorPerfilService;
    }

    // Disponible en el modelo de todas las páginas de este controlador (topbar).
    @org.springframework.web.bind.annotation.ModelAttribute("admin")
    public com.pucp.skillb_ia.model.Usuario admin(@AuthenticationPrincipal UsuarioDetails principal) {
        return principal != null ? principal.getUsuario() : null;
    }

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping({"/dashboard", "/admin-dashboard.html"})
    public String dashboard(Model model) {
        model.addAttribute("resumenUsuarios", adminUsuarioService.resumen());
        model.addAttribute("usuariosPendientes", adminUsuarioService.listar().stream()
                .filter(AdminUsuarioService.UsuarioFila::pendiente)
                .limit(5)
                .toList());

        model.addAttribute("resumenHabilidades", adminHabilidadService.resumen());

        List<AdminAuditoriaService.LogFila> logs = adminAuditoriaService.listar(
                new AdminAuditoriaService.FiltrosAuditoria(null, null, null, null, null));
        model.addAttribute("ultimasAcciones", logs.stream().limit(4).toList());
        model.addAttribute("accionesHoy", logs.stream()
                .filter(l -> l.fechaSolo().isEqual(LocalDate.now()))
                .count());

        return "admin/admin-dashboard";
    }

    // ============================================================
    // USUARIOS (Épica 5)
    // ============================================================

    @GetMapping({"/usuarios", "/admin-usuarios.html"})
    public String users(Model model) {
        model.addAttribute("usuarios", adminUsuarioService.listar());
        model.addAttribute("resumen", adminUsuarioService.resumen());
        model.addAttribute("rolesDisponibles", AdminUsuarioService.etiquetasRoles());
        model.addAttribute("rolesAsignables", AdminUsuarioService.etiquetasRolesAsignables());
        model.addAttribute("cargosActivos", adminCargoService.listarCargosActivos());
        return "admin/admin-usuarios";
    }

    @PostMapping("/usuarios/cargo")
    public String asignarCargoUsuario(@RequestParam Long usuarioId, @RequestParam Long cargoId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes redirectAttributes) {
        try {
            adminUsuarioService.asignarCargo(usuarioId, cargoId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se actualizó el cargo del colaborador.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios")
    public String crearUsuario(@RequestParam String correo, @RequestParam String rol,
                                @AuthenticationPrincipal UsuarioDetails principal,
                                RedirectAttributes redirectAttributes) {
        try {
            adminUsuarioService.crear(correo, rol, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se creó el usuario " + correo + " y se envió su correo de invitación.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/carga-masiva")
    public String cargaMasivaUsuarios(@RequestParam("archivo") MultipartFile archivo,
                                       @AuthenticationPrincipal UsuarioDetails principal,
                                       RedirectAttributes redirectAttributes) {
        if (archivo.isEmpty()) {
            redirectAttributes.addFlashAttribute("mensajeError", "Selecciona un archivo CSV para la carga masiva.");
            return "redirect:/admin/usuarios";
        }
        try {
            AdminUsuarioService.ResultadoCargaMasiva resultado =
                    adminUsuarioService.cargaMasiva(archivo, principal.getUsuario());
            if (resultado.tieneErrores()) {
                redirectAttributes.addFlashAttribute("cargaErrores", resultado.errores());
            } else {
                redirectAttributes.addFlashAttribute("mensajeOk",
                        "Se crearon " + resultado.creados() + " usuario(s) desde el archivo.");
            }
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("mensajeError", "No se pudo leer el archivo: " + e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/cambiar-rol")
    public String cambiarRolUsuario(@RequestParam Long usuarioId, @RequestParam String rol,
                                     @AuthenticationPrincipal UsuarioDetails principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            adminUsuarioService.cambiarRol(usuarioId, rol, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se actualizó el rol del usuario.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/desactivar")
    public String desactivarUsuario(@RequestParam Long usuarioId,
                                     @AuthenticationPrincipal UsuarioDetails principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            adminUsuarioService.desactivar(usuarioId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se desactivó el usuario.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/reactivar")
    public String reactivarUsuario(@RequestParam Long usuarioId,
                                    @AuthenticationPrincipal UsuarioDetails principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            adminUsuarioService.reactivar(usuarioId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se reactivó el usuario.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/reenviar")
    public String reenviarActivacion(@RequestParam Long usuarioId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes redirectAttributes) {
        try {
            adminUsuarioService.reenviarActivacion(usuarioId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se reenvió el enlace de activación.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    // ============================================================
    // HABILIDADES (Épica 5)
    // ============================================================

    @GetMapping({"/habilidades", "/admin-habilidades.html"})
    public String skills(Model model) {
        model.addAttribute("habilidades", adminHabilidadService.listar());
        model.addAttribute("resumen", adminHabilidadService.resumen());
        model.addAttribute("categorias", adminHabilidadService.listarCategoriasActivas());
        model.addAttribute("todasCategorias", adminHabilidadService.listarCategorias());
        model.addAttribute("cargos", adminCargoService.listarTodosLosCargos());
        return "admin/admin-habilidades";
    }

    @PostMapping("/habilidades/categorias")
    public String crearCategoria(@RequestParam String nombre,
                                  @RequestParam(required = false) String descripcion,
                                  @AuthenticationPrincipal UsuarioDetails principal,
                                  RedirectAttributes redirectAttributes) {
        try {
            adminHabilidadService.crearCategoria(nombre, descripcion, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se creó la categoría \"" + nombre + "\".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/habilidades";
    }

    @PostMapping("/habilidades/categorias/editar")
    public String editarCategoria(@RequestParam Long categoriaId, @RequestParam String nombre,
                                   @RequestParam(required = false) String descripcion,
                                   @AuthenticationPrincipal UsuarioDetails principal,
                                   RedirectAttributes redirectAttributes) {
        try {
            adminHabilidadService.editarCategoria(categoriaId, nombre, descripcion, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se actualizó la categoría.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/habilidades";
    }

    @PostMapping("/habilidades/categorias/desactivar")
    public String desactivarCategoria(@RequestParam Long categoriaId,
                                       @AuthenticationPrincipal UsuarioDetails principal,
                                       RedirectAttributes redirectAttributes) {
        try {
            adminHabilidadService.desactivarCategoria(categoriaId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se desactivó la categoría.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/habilidades";
    }

    @PostMapping("/habilidades/categorias/reactivar")
    public String reactivarCategoria(@RequestParam Long categoriaId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes redirectAttributes) {
        try {
            adminHabilidadService.reactivarCategoria(categoriaId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se reactivó la categoría.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/habilidades";
    }

    @PostMapping("/habilidades")
    public String crearHabilidad(@RequestParam String nombre, @RequestParam Long categoriaId,
                                  @AuthenticationPrincipal UsuarioDetails principal,
                                  RedirectAttributes redirectAttributes) {
        try {
            adminHabilidadService.crear(nombre, categoriaId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se creó la habilidad \"" + nombre + "\".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/habilidades";
    }

    @PostMapping("/habilidades/editar")
    public String editarHabilidad(@RequestParam Long habilidadId, @RequestParam String nombre,
                                   @RequestParam Long categoriaId,
                                   @AuthenticationPrincipal UsuarioDetails principal,
                                   RedirectAttributes redirectAttributes) {
        try {
            adminHabilidadService.editar(habilidadId, nombre, categoriaId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se actualizó la habilidad.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/habilidades";
    }

    @PostMapping("/habilidades/desactivar")
    public String desactivarHabilidad(@RequestParam Long habilidadId,
                                       @AuthenticationPrincipal UsuarioDetails principal,
                                       RedirectAttributes redirectAttributes) {
        try {
            adminHabilidadService.desactivar(habilidadId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se desactivó la habilidad.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/habilidades";
    }

    @PostMapping("/habilidades/reactivar")
    public String reactivarHabilidad(@RequestParam Long habilidadId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes redirectAttributes) {
        try {
            adminHabilidadService.reactivar(habilidadId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se reactivó la habilidad.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/habilidades";
    }

    // ============================================================
    // CARGOS Y MATRIZ SALARIAL (Épica 5) — pestaña de admin-habilidades
    // ============================================================

    private static final String REDIRECT_CARGOS = "redirect:/admin/habilidades?tab=cargos";

    @PostMapping("/cargos/crear")
    public String crearCargo(@RequestParam String nombre,
                             @RequestParam(required = false) BigDecimal sueldoJunior,
                             @RequestParam(required = false) BigDecimal sueldoSemiSenior,
                             @RequestParam(required = false) BigDecimal sueldoSenior,
                             @AuthenticationPrincipal UsuarioDetails principal,
                             RedirectAttributes redirectAttributes) {
        try {
            adminCargoService.crear(nombre, sueldoJunior, sueldoSemiSenior, sueldoSenior, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se creó el cargo \"" + nombre.strip() + "\".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return REDIRECT_CARGOS;
    }

    @PostMapping("/cargos/editar")
    public String editarCargo(@RequestParam Long id,
                              @RequestParam(required = false) BigDecimal sueldoJunior,
                              @RequestParam(required = false) BigDecimal sueldoSemiSenior,
                              @RequestParam(required = false) BigDecimal sueldoSenior,
                              @AuthenticationPrincipal UsuarioDetails principal,
                              RedirectAttributes redirectAttributes) {
        try {
            int recalculados = adminCargoService.editarTarifas(id, sueldoJunior, sueldoSemiSenior, sueldoSenior,
                    principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se actualizaron las tarifas del cargo"
                    + (recalculados > 0 ? " y se recalculó el sueldo de " + recalculados + " colaborador(es)." : "."));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return REDIRECT_CARGOS;
    }

    @PostMapping("/cargos/alternar")
    public String alternarCargo(@RequestParam Long id,
                                @AuthenticationPrincipal UsuarioDetails principal,
                                RedirectAttributes redirectAttributes) {
        try {
            boolean activo = adminCargoService.alternarEstado(id, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", activo ? "Se reactivó el cargo." : "Se desactivó el cargo.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return REDIRECT_CARGOS;
    }

    // ============================================================
    // CONFIGURACIÓN (Épica 5, C10)
    // ============================================================

    @GetMapping({"/configuracion", "/admin-configuracion.html"})
    public String configuration(Model model) {
        model.addAttribute("parametros", adminConfiguracionService.listar());
        model.addAttribute("historial", adminConfiguracionService.historialReciente());
        return "admin/admin-configuracion";
    }

    @PostMapping("/configuracion/crear")
    public String crearConfiguracion(@RequestParam String clave, @RequestParam(required = false) String descripcion,
                                      @RequestParam String valor,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes redirectAttributes) {
        try {
            adminConfiguracionService.crear(clave, descripcion, valor, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se creó el parámetro.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/configuracion";
    }

    @PostMapping("/configuracion/editar")
    public String editarConfiguracion(@RequestParam Long parametroId, @RequestParam String valor,
                                       @AuthenticationPrincipal UsuarioDetails principal,
                                       RedirectAttributes redirectAttributes) {
        try {
            adminConfiguracionService.editar(parametroId, valor, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se actualizó el parámetro.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/configuracion";
    }

    // ============================================================
    // AUDITORÍA (Épica 5, solo lectura)
    // ============================================================

    @GetMapping({"/auditoria", "/admin-auditoria.html"})
    public String audit(@RequestParam(required = false) String texto,
                         @RequestParam(required = false) String rol,
                         @RequestParam(required = false) String accion,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                         @RequestParam(defaultValue = "1") int pagina,
                         Model model) {
        AdminAuditoriaService.FiltrosAuditoria filtros =
                new AdminAuditoriaService.FiltrosAuditoria(texto, rol, accion, desde, hasta);
        AdminAuditoriaService.PaginaLogs paginaLogs = adminAuditoriaService.listarPagina(filtros, pagina);
        model.addAttribute("logs", paginaLogs.filas());
        model.addAttribute("paginaActual", paginaLogs.paginaActual());
        model.addAttribute("totalPaginas", paginaLogs.totalPaginas());
        model.addAttribute("totalRegistros", paginaLogs.totalRegistros());
        model.addAttribute("tamanioPagina", AdminAuditoriaService.TAMANIO_PAGINA);
        model.addAttribute("tiposAccion", adminAuditoriaService.tiposDeAccionDisponibles());
        model.addAttribute("rolesDisponibles", AdminUsuarioService.etiquetasRoles());
        model.addAttribute("texto", texto);
        model.addAttribute("rol", rol);
        model.addAttribute("accion", accion);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        return "admin/admin-auditoria";
    }

    @GetMapping("/auditoria/exportar")
    public void exportarAuditoria(@RequestParam(required = false) String texto,
                                   @RequestParam(required = false) String rol,
                                   @RequestParam(required = false) String accion,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                   HttpServletResponse response) throws IOException {
        List<AdminAuditoriaService.LogFila> logs = adminAuditoriaService.listar(
                new AdminAuditoriaService.FiltrosAuditoria(texto, rol, accion, desde, hasta));

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"auditoria.csv\"");

        java.io.OutputStream out = response.getOutputStream();
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF);

        StringBuilder csv = new StringBuilder("Fecha y hora,Usuario,Rol,Tipo de accion,Detalle\n");
        for (AdminAuditoriaService.LogFila log : logs) {
            csv.append(csvEscapar(log.fecha())).append(',')
                    .append(csvEscapar(log.usuarioNombre())).append(',')
                    .append(csvEscapar(log.rolEtiqueta())).append(',')
                    .append(csvEscapar(log.accionEtiqueta())).append(',')
                    .append(csvEscapar(log.detalle())).append('\n');
        }
        out.write(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.flush();
    }

    @GetMapping("/auditoria/exportar-excel")
    public void exportarAuditoriaExcel(@RequestParam(required = false) String texto,
                                        @RequestParam(required = false) String rol,
                                        @RequestParam(required = false) String accion,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                        HttpServletResponse response) throws IOException {
        List<AdminAuditoriaService.LogFila> logs = adminAuditoriaService.listar(
                new AdminAuditoriaService.FiltrosAuditoria(texto, rol, accion, desde, hasta));

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"auditoria.xlsx\"");

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook libro = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet hoja = libro.createSheet("Auditoria");
            String[] encabezados = {"Fecha y hora", "Usuario", "Rol", "Tipo de accion", "Detalle"};
            org.apache.poi.ss.usermodel.Row filaEncabezado = hoja.createRow(0);
            for (int i = 0; i < encabezados.length; i++) {
                filaEncabezado.createCell(i).setCellValue(encabezados[i]);
            }

            int numeroFila = 1;
            for (AdminAuditoriaService.LogFila log : logs) {
                org.apache.poi.ss.usermodel.Row fila = hoja.createRow(numeroFila++);
                fila.createCell(0).setCellValue(log.fecha());
                fila.createCell(1).setCellValue(log.usuarioNombre());
                fila.createCell(2).setCellValue(log.rolEtiqueta());
                fila.createCell(3).setCellValue(log.accionEtiqueta());
                fila.createCell(4).setCellValue(log.detalle() != null ? log.detalle() : "");
            }
            for (int i = 0; i < encabezados.length; i++) {
                hoja.autoSizeColumn(i);
            }

            libro.write(response.getOutputStream());
        }
    }

    // ============================================================
    // PERFIL
    // ============================================================

    @GetMapping({"/perfil", "/admin-perfil.html"})
    public String perfil() {
        return "admin/admin-perfil";
    }

    @PostMapping("/perfil/telefono")
    public String actualizarTelefonoAdmin(@RequestParam(value = "telefono", required = false) String telefono,
                                           @AuthenticationPrincipal UsuarioDetails principal,
                                           RedirectAttributes redirectAttributes) {
        try {
            adminPerfilService.actualizarTelefono(telefono, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeExito", "Teléfono actualizado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/perfil";
    }

    @PostMapping("/perfil/foto")
    public String actualizarFotoAdmin(@RequestParam("foto") MultipartFile foto,
                                       @AuthenticationPrincipal UsuarioDetails principal,
                                       RedirectAttributes redirectAttributes) {
        try {
            adminPerfilService.actualizarFoto(foto, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeExito", "Foto de perfil actualizada.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/perfil";
    }

    @PostMapping("/perfil/password")
    public String actualizarPasswordAdmin(@RequestParam("passwordActual") String passwordActual,
                                           @RequestParam("passwordNueva") String passwordNueva,
                                           @RequestParam("passwordConfirm") String passwordConfirm,
                                           @AuthenticationPrincipal UsuarioDetails principal,
                                           RedirectAttributes redirectAttributes) {
        try {
            if (!passwordNueva.equals(passwordConfirm)) {
                throw new IllegalArgumentException("Las contraseñas nuevas no coinciden.");
            }
            adminPerfilService.actualizarPassword(passwordActual, passwordNueva, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeExito", "Contraseña actualizada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/perfil";
    }

    private String csvEscapar(String valor) {
        if (valor == null) return "";
        return "\"" + valor.replace("\"", "\"\"") + "\"";
    }

    // ================== CURSOS ==================
    @GetMapping({"/cursos", "/admin-cursos.html"})
    public String cursos(Model model) {
        model.addAttribute("cursos", adminCursoService.listarTodos());
        model.addAttribute("modalidades", com.pucp.skillb_ia.model.enums.ModalidadCurso.values());
        return "admin/admin-cursos";
    }

    @PostMapping("/cursos/crear")
    public String crearCurso(@ModelAttribute Curso curso,
                             @AuthenticationPrincipal UsuarioDetails principal,
                             RedirectAttributes ra) {
        try {
            adminCursoService.crear(curso, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "Se creó el curso exitosamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/cursos";
    }

    @PostMapping("/cursos/editar")
    public String editarCurso(@RequestParam("cursoId") Long cursoId,
                              @ModelAttribute Curso curso,
                              @AuthenticationPrincipal UsuarioDetails principal,
                              RedirectAttributes ra) {
        try {
            adminCursoService.editar(cursoId, curso, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "Se guardaron los cambios del curso.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/cursos";
    }

    @PostMapping("/cursos/alternar")
    public String alternarEstadoCurso(@RequestParam("cursoId") Long cursoId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes ra) {
        try {
            adminCursoService.alternarEstado(cursoId, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "El estado del curso ha sido actualizado.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/cursos";
    }

    // ============================================================
    // CV Y EXPERIENCIA PROFESIONAL DE COLABORADORES
    // ============================================================

    @GetMapping({"/experiencia", "/admin-experiencia.html"})
    public String experiencia(Model model) {
        model.addAttribute("cvsPendientes", adminUsuarioService.listarCvsPendientes());
        model.addAttribute("cvsRevisados", adminUsuarioService.listarCvsRevisados());
        return "admin/admin-experiencia";
    }

    // Ojo: el path variable NO se llama "id" a propósito — @ModelAttribute
    // ExperienciaProfesional también tiene un campo "id", y Spring Data Binder
    // pisa ese campo con cualquier atributo implícito del modelo que se llame
    // igual (incluidos los @PathVariable), hacendo que el repository intente
    // actualizar una fila ajena en vez de crear una nueva.
    @GetMapping("/experiencia/{colaboradorId}")
    public String experienciaDetalle(@PathVariable Long colaboradorId, Model model) {
        Usuario colaborador = adminUsuarioService.obtenerUsuario(colaboradorId);
        model.addAttribute("colaborador", colaborador);
        model.addAttribute("experiencias", colaboradorPerfilService.listarExperienciaProfesional(colaborador));
        return "admin/admin-experiencia-detalle";
    }

    @PostMapping("/experiencia/{colaboradorId}/agregar")
    public String agregarExperiencia(@PathVariable Long colaboradorId,
                                     @ModelAttribute ExperienciaProfesional exp,
                                     RedirectAttributes ra) {
        try {
            Usuario colaborador = adminUsuarioService.obtenerUsuario(colaboradorId);
            colaboradorPerfilService.agregarExperiencia(colaborador, exp);
            ra.addFlashAttribute("mensajeOk", "Se agregó la experiencia profesional.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/experiencia/" + colaboradorId;
    }

    @PostMapping("/experiencia/{colaboradorId}/revisar")
    public String marcarCvRevisado(@PathVariable Long colaboradorId,
                                   @AuthenticationPrincipal UsuarioDetails principal,
                                   RedirectAttributes ra) {
        try {
            adminUsuarioService.marcarCvRevisado(colaboradorId, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "Se marcó el CV como revisado.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/experiencia";
    }
}
