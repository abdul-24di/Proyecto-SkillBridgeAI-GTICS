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
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import com.pucp.skillb_ia.dto.auth.PasswordPerfilForm;
import com.pucp.skillb_ia.dto.auth.TelefonoPerfilForm;
import jakarta.validation.Valid;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static com.pucp.skillb_ia.controller.GlobalExceptionHandler.mensajeSeguro;

/**
 * Vistas y formularios del Administrador ({@code /admin/**}): dashboard, usuarios, catálogos, configuración,
 * auditoría, reportes, perfil, cursos, revisión de pre-registros y foros. Las reglas de negocio están en
 * los servicios; aquí se arma el modelo, se muestran los mensajes y se redirige.
 */
@Controller
@RequestMapping("/admin")
public class AdminViewController {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.MessageSource messageSource;

    private final AdminUsuarioService adminUsuarioService;
    private final AdminHabilidadService adminHabilidadService;
    private final AdminCargoService adminCargoService;
    private final AdminConfiguracionService adminConfiguracionService;
    private final AdminAuditoriaService adminAuditoriaService;
    private final com.pucp.skillb_ia.service.AdminReporteService adminReporteService;
    private final AdminPerfilService adminPerfilService;
    private final AdminCursoService adminCursoService;
    private final ColaboradorPerfilService colaboradorPerfilService;
    private final com.pucp.skillb_ia.service.admin.AdminForoService adminForoService;
    private final com.pucp.skillb_ia.service.pm.PmForoService pmForoService;
    private final com.pucp.skillb_ia.service.DniVerificacionService dniVerificacionService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public AdminViewController(AdminUsuarioService adminUsuarioService, AdminHabilidadService adminHabilidadService,
                                AdminConfiguracionService adminConfiguracionService,
                                AdminAuditoriaService adminAuditoriaService,
                                AdminPerfilService adminPerfilService,
                                AdminCargoService adminCargoService,
                                AdminCursoService adminCursoService,
                                ColaboradorPerfilService colaboradorPerfilService,
                                com.pucp.skillb_ia.service.admin.AdminForoService adminForoService,
                                com.pucp.skillb_ia.service.pm.PmForoService pmForoService,
                                com.pucp.skillb_ia.service.AdminReporteService adminReporteService,
                                com.pucp.skillb_ia.service.DniVerificacionService dniVerificacionService) {
        this.adminReporteService = adminReporteService;
        this.dniVerificacionService = dniVerificacionService;
        this.adminUsuarioService = adminUsuarioService;
        this.adminHabilidadService = adminHabilidadService;
        this.adminConfiguracionService = adminConfiguracionService;
        this.adminAuditoriaService = adminAuditoriaService;
        this.adminCargoService = adminCargoService;
        this.adminPerfilService = adminPerfilService;
        this.adminCursoService = adminCursoService;
        this.colaboradorPerfilService = colaboradorPerfilService;
        this.adminForoService = adminForoService;
        this.pmForoService = pmForoService;
    }

    /**
     * Deja al Administrador de la sesión en el modelo.
     * <p>
     * Disponible en el modelo de todas las páginas de este controlador (topbar).
     *
     * @param principal usuario con la sesión iniciada
     * @return el Administrador o {@code null} si no hay sesión
     */
    @org.springframework.web.bind.annotation.ModelAttribute("admin")
    public com.pucp.skillb_ia.model.Usuario admin(@AuthenticationPrincipal UsuarioDetails principal) {
        return principal != null ? principal.getUsuario() : null;
    }

    /**
     * Redirige la raíz del área al dashboard.
     *
     * @return redirección a {@code /admin/dashboard}
     */
    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/admin/dashboard";
    }

    /**
     * Muestra el dashboard: resumen de usuarios y habilidades, cinco cuentas pendientes de activar, las cuatro
     * últimas acciones de auditoría, usuarios por rol, actividad de los últimos siete días y pre-registros por
     * revisar.
     *
     * @param model modelo con los datos de la vista
     * @return vista {@code admin/admin-dashboard}
     */
    @GetMapping({"/dashboard", "/admin-dashboard.html"})
    public String dashboard(Model model) {
        // Fecha de hoy para el enlace "Acciones auditadas hoy" → auditoría filtrada por hoy.
        model.addAttribute("hoy", LocalDate.now());
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

        // Gráficos del dashboard y pre-registros que esperan la revisión del Admin.
        model.addAttribute("usuariosPorRol", adminUsuarioService.usuariosPorRol());
        model.addAttribute("actividadSemana", adminAuditoriaService.actividadPorDia(logs, 7));
        model.addAttribute("preRegistrosPendientes", adminUsuarioService.listarCvsPendientes().size());

        return "admin/admin-dashboard";
    }

    // ============================================================
    // USUARIOS (Épica 5)
    // ============================================================

    /**
     * Muestra el directorio de usuarios, filtrado y paginado.
     *
     * @param busqueda texto a buscar; {@code null} o vacío para no filtrar
     * @param rol etiqueta del rol; en los listados, {@code null} para no filtrar
     * @param estado estado por el que se filtra; {@code null} para no filtrar
     * @param pagina número de página, desde 1
     * @param model modelo con los datos de la vista
     * @return vista {@code admin/admin-usuarios}
     */
    @GetMapping({"/usuarios", "/admin-usuarios.html"})
    public String users(@RequestParam(required = false) String busqueda,
                        @RequestParam(required = false) String rol,
                        @RequestParam(required = false) String estado,
                        @RequestParam(required = false) Integer pagina,
                        Model model) {
        Page<AdminUsuarioService.UsuarioFila> paginaUsuarios = adminUsuarioService.listarPagina(busqueda, rol, estado, pagina);
        model.addAttribute("paginaUsuarios", paginaUsuarios);
        model.addAttribute("usuarios", paginaUsuarios.getContent());
        model.addAttribute("busqueda", busqueda);
        model.addAttribute("rolFiltro", rol);
        model.addAttribute("estadoFiltro", estado);
        model.addAttribute("resumen", adminUsuarioService.resumen());
        model.addAttribute("rolesDisponibles", AdminUsuarioService.etiquetasRoles());
        model.addAttribute("rolesAsignables", AdminUsuarioService.etiquetasRolesAsignables());
        model.addAttribute("cargosActivos", adminCargoService.listarCargosActivos());
        return "admin/admin-usuarios";
    }

    /**
     * Asigna un cargo a un colaborador; su sueldo base se recalcula con la tarifa del cargo.
     *
     * @param usuarioId id del usuario
     * @param cargoId id del cargo
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al directorio de usuarios
     */
    @PostMapping("/usuarios/cargo")
    public String asignarCargoUsuario(@RequestParam Long usuarioId, @RequestParam Long cargoId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminUsuarioService.asignarCargo(usuarioId, cargoId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se actualizó el cargo del colaborador.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/usuarios";
    }

    private static final String MENSAJE_CORREO_NO_ENVIADO = "%s no se pudo enviar el correo de invitación. "
            + "Revisa la configuración del correo del servidor (MAIL_USERNAME y MAIL_PASSWORD) y usa \"Reenviar enlace\".";

    /**
     * Crea una cuenta por invitación. Si el correo no se pudo enviar, la cuenta queda creada y se avisa en
     * pantalla.
     *
     * @param correo correo del usuario
     * @param rol etiqueta del rol; en los listados, {@code null} para no filtrar
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al directorio de usuarios
     */
    @PostMapping("/usuarios")
    public String crearUsuario(@RequestParam String correo, @RequestParam String rol,
                                @AuthenticationPrincipal UsuarioDetails principal,
                                RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            if (adminUsuarioService.crear(correo, rol, principal.getUsuario())) {
                redirectAttributes.addFlashAttribute("mensajeOk", "Se creó el usuario " + correo + " y se envió su correo de invitación.");
            } else {
                redirectAttributes.addFlashAttribute("mensajeError", MENSAJE_CORREO_NO_ENVIADO
                        .formatted("Se creó el usuario " + correo + ", pero"));
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/usuarios";
    }

    /**
     * Crea cuentas desde un archivo CSV o Excel. Con un solo error no se crea ninguna y se listan las filas por
     * corregir.
     *
     * @param archivo archivo subido
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al directorio de usuarios
     */
    @PostMapping("/usuarios/carga-masiva")
    public String cargaMasivaUsuarios(@RequestParam("archivo") MultipartFile archivo,
                                       @AuthenticationPrincipal UsuarioDetails principal,
                                       RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (archivo.isEmpty()) {
            redirectAttributes.addFlashAttribute("mensajeError", "Selecciona un archivo CSV para la carga masiva.");
            return "redirect:/admin/usuarios";
        }
        try {
            AdminUsuarioService.ResultadoCargaMasiva resultado =
                    adminUsuarioService.cargaMasiva(archivo, principal.getUsuario());
            if (resultado.tieneErrores()) {
                redirectAttributes.addFlashAttribute("cargaErrores", resultado.errores());
            } else if (resultado.sinCorreo() > 0) {
                redirectAttributes.addFlashAttribute("mensajeError", MENSAJE_CORREO_NO_ENVIADO.formatted(
                        "Se crearon " + resultado.creados() + " usuario(s), pero a " + resultado.sinCorreo() + " de ellos"));
            } else {
                redirectAttributes.addFlashAttribute("mensajeOk",
                        "Se crearon " + resultado.creados() + " usuario(s) desde el archivo.");
            }
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("mensajeError", "No se pudo leer el archivo: " + e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    /**
     * Cambia el rol de un usuario.
     *
     * @param usuarioId id del usuario
     * @param rol etiqueta del rol; en los listados, {@code null} para no filtrar
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al directorio de usuarios
     */
    @PostMapping("/usuarios/cambiar-rol")
    public String cambiarRolUsuario(@RequestParam Long usuarioId, @RequestParam String rol,
                                     @AuthenticationPrincipal UsuarioDetails principal,
                                     RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminUsuarioService.cambiarRol(usuarioId, rol, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se actualizó el rol del usuario.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/usuarios";
    }

    /**
     * Desactiva una cuenta: el usuario pierde el acceso y se cierran sus sesiones.
     *
     * @param usuarioId id del usuario
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al directorio de usuarios
     */
    @PostMapping("/usuarios/desactivar")
    public String desactivarUsuario(@RequestParam Long usuarioId,
                                     @AuthenticationPrincipal UsuarioDetails principal,
                                     RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminUsuarioService.desactivar(usuarioId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se desactivó el usuario.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/usuarios";
    }

    /**
     * Reactiva una cuenta desactivada.
     *
     * @param usuarioId id del usuario
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al directorio de usuarios
     */
    @PostMapping("/usuarios/reactivar")
    public String reactivarUsuario(@RequestParam Long usuarioId,
                                    @AuthenticationPrincipal UsuarioDetails principal,
                                    RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminUsuarioService.reactivar(usuarioId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se reactivó el usuario.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/usuarios";
    }

    /**
     * Genera y envía un nuevo enlace de activación; el anterior deja de servir.
     *
     * @param usuarioId id del usuario
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al directorio de usuarios
     */
    @PostMapping("/usuarios/reenviar")
    public String reenviarActivacion(@RequestParam Long usuarioId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            if (adminUsuarioService.reenviarActivacion(usuarioId, principal.getUsuario())) {
                redirectAttributes.addFlashAttribute("mensajeOk", "Se reenvió el enlace de activación.");
            } else {
                redirectAttributes.addFlashAttribute("mensajeError",
                        MENSAJE_CORREO_NO_ENVIADO.formatted("Se generó un enlace nuevo, pero"));
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/usuarios";
    }

    // ============================================================
    // HABILIDADES (Épica 5)
    // ============================================================

    /**
     * Muestra el catálogo de habilidades y categorías y la matriz salarial de los cargos.
     *
     * @param busqueda texto a buscar; {@code null} o vacío para no filtrar
     * @param categoria categoría por la que se filtra; {@code null} para no filtrar
     * @param estado estado por el que se filtra; {@code null} para no filtrar
     * @param pagina número de página, desde 1
     * @param paginaCategorias página de la tabla de categorías
     * @param model modelo con los datos de la vista
     * @return vista {@code admin/admin-habilidades}
     */
    @GetMapping({"/habilidades", "/admin-habilidades.html"})
    public String skills(@RequestParam(required = false) String busqueda,
                         @RequestParam(required = false) String categoria,
                         @RequestParam(required = false) String estado,
                         @RequestParam(required = false) Integer pagina,
                         @RequestParam(required = false) Integer paginaCategorias,
                         Model model) {
        Page<AdminHabilidadService.HabilidadFila> paginaHabilidades = adminHabilidadService.listarPagina(busqueda, categoria, estado, pagina);
        Page<AdminHabilidadService.CategoriaFila> paginaCat = com.pucp.skillb_ia.util.Paginador.paginar(adminHabilidadService.listarCategorias(), paginaCategorias);
        model.addAttribute("paginaHabilidades", paginaHabilidades);
        model.addAttribute("habilidades", paginaHabilidades.getContent());
        model.addAttribute("paginaCategorias", paginaCat);
        model.addAttribute("todasCategorias", paginaCat.getContent());
        model.addAttribute("busqueda", busqueda);
        model.addAttribute("categoriaFiltro", categoria);
        model.addAttribute("estadoFiltro", estado);
        model.addAttribute("resumen", adminHabilidadService.resumen());
        model.addAttribute("categorias", adminHabilidadService.listarCategoriasActivas());
        model.addAttribute("cargos", adminCargoService.listarTodosLosCargos());
        return "admin/admin-habilidades";
    }

    /**
     * Crea una categoría de habilidades.
     *
     * @param nombre nombre ingresado
     * @param descripcion descripción; puede ser {@code null} o vacía
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al catálogo
     */
    @PostMapping("/habilidades/categorias")
    public String crearCategoria(@RequestParam String nombre,
                                  @RequestParam(required = false) String descripcion,
                                  @AuthenticationPrincipal UsuarioDetails principal,
                                  RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminHabilidadService.crearCategoria(nombre, descripcion, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se creó la categoría \"" + nombre + "\".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/habilidades";
    }

    /**
     * Cambia el nombre o la descripción de una categoría.
     *
     * @param categoriaId id de la categoría
     * @param nombre nombre ingresado
     * @param descripcion descripción; puede ser {@code null} o vacía
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al catálogo
     */
    @PostMapping("/habilidades/categorias/editar")
    public String editarCategoria(@RequestParam Long categoriaId, @RequestParam String nombre,
                                   @RequestParam(required = false) String descripcion,
                                   @AuthenticationPrincipal UsuarioDetails principal,
                                   RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminHabilidadService.editarCategoria(categoriaId, nombre, descripcion, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se actualizó la categoría.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/habilidades";
    }

    /**
     * Desactiva una categoría que no tenga habilidades asociadas.
     *
     * @param categoriaId id de la categoría
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al catálogo
     */
    @PostMapping("/habilidades/categorias/desactivar")
    public String desactivarCategoria(@RequestParam Long categoriaId,
                                       @AuthenticationPrincipal UsuarioDetails principal,
                                       RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminHabilidadService.desactivarCategoria(categoriaId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se desactivó la categoría.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/habilidades";
    }

    /**
     * Reactiva una categoría.
     *
     * @param categoriaId id de la categoría
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al catálogo
     */
    @PostMapping("/habilidades/categorias/reactivar")
    public String reactivarCategoria(@RequestParam Long categoriaId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminHabilidadService.reactivarCategoria(categoriaId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se reactivó la categoría.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/habilidades";
    }

    /**
     * Crea una habilidad; su nombre no puede repetirse en el catálogo.
     *
     * @param nombre nombre ingresado
     * @param categoriaId id de la categoría
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al catálogo
     */
    @PostMapping("/habilidades")
    public String crearHabilidad(@RequestParam String nombre, @RequestParam Long categoriaId,
                                  @AuthenticationPrincipal UsuarioDetails principal,
                                  RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminHabilidadService.crear(nombre, categoriaId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se creó la habilidad \"" + nombre + "\".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/habilidades";
    }

    /**
     * Cambia el nombre o la categoría de una habilidad.
     *
     * @param habilidadId id de la habilidad
     * @param nombre nombre ingresado
     * @param categoriaId id de la categoría
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al catálogo
     */
    @PostMapping("/habilidades/editar")
    public String editarHabilidad(@RequestParam Long habilidadId, @RequestParam String nombre,
                                   @RequestParam Long categoriaId,
                                   @AuthenticationPrincipal UsuarioDetails principal,
                                   RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminHabilidadService.editar(habilidadId, nombre, categoriaId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se actualizó la habilidad.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/habilidades";
    }

    /**
     * Desactiva una habilidad: se conserva en los perfiles que ya la tienen.
     *
     * @param habilidadId id de la habilidad
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al catálogo
     */
    @PostMapping("/habilidades/desactivar")
    public String desactivarHabilidad(@RequestParam Long habilidadId,
                                       @AuthenticationPrincipal UsuarioDetails principal,
                                       RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminHabilidadService.desactivar(habilidadId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se desactivó la habilidad.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/habilidades";
    }

    /**
     * Reactiva una habilidad.
     *
     * @param habilidadId id de la habilidad
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al catálogo
     */
    @PostMapping("/habilidades/reactivar")
    public String reactivarHabilidad(@RequestParam Long habilidadId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminHabilidadService.reactivar(habilidadId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se reactivó la habilidad.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/habilidades";
    }

    // ============================================================
    // CARGOS Y MATRIZ SALARIAL (Épica 5) — pestaña de admin-habilidades
    // ============================================================

    private static final String REDIRECT_CARGOS = "redirect:/admin/habilidades?tab=cargos";

    /**
     * Crea un cargo con sus tres tarifas.
     *
     * @param nombre nombre ingresado
     * @param sueldoJunior tarifa mensual del nivel Junior
     * @param sueldoSemiSenior tarifa mensual del nivel Semi-Senior
     * @param sueldoSenior tarifa mensual del nivel Senior
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección a la pestaña de cargos
     */
    @PostMapping("/cargos/crear")
    public String crearCargo(@RequestParam String nombre,
                             @RequestParam(required = false) BigDecimal sueldoJunior,
                             @RequestParam(required = false) BigDecimal sueldoSemiSenior,
                             @RequestParam(required = false) BigDecimal sueldoSenior,
                             @AuthenticationPrincipal UsuarioDetails principal,
                             RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminCargoService.crear(nombre, sueldoJunior, sueldoSemiSenior, sueldoSenior, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se creó el cargo \"" + nombre.strip() + "\".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return REDIRECT_CARGOS;
    }

    /**
     * Cambia las tarifas de un cargo e informa a cuántos colaboradores se les recalculó el sueldo.
     *
     * @param id id del registro
     * @param sueldoJunior tarifa mensual del nivel Junior
     * @param sueldoSemiSenior tarifa mensual del nivel Semi-Senior
     * @param sueldoSenior tarifa mensual del nivel Senior
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección a la pestaña de cargos
     */
    @PostMapping("/cargos/editar")
    public String editarCargo(@RequestParam Long id,
                              @RequestParam(required = false) BigDecimal sueldoJunior,
                              @RequestParam(required = false) BigDecimal sueldoSemiSenior,
                              @RequestParam(required = false) BigDecimal sueldoSenior,
                              @AuthenticationPrincipal UsuarioDetails principal,
                              RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            int recalculados = adminCargoService.editarTarifas(id, sueldoJunior, sueldoSemiSenior, sueldoSenior,
                    principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se actualizaron las tarifas del cargo"
                    + (recalculados > 0 ? " y se recalculó el sueldo de " + recalculados + " colaborador(es)." : "."));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return REDIRECT_CARGOS;
    }

    /**
     * Activa o desactiva un cargo.
     *
     * @param id id del registro
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección a la pestaña de cargos
     */
    @PostMapping("/cargos/alternar")
    public String alternarCargo(@RequestParam Long id,
                                @AuthenticationPrincipal UsuarioDetails principal,
                                RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            boolean activo = adminCargoService.alternarEstado(id, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", activo ? "Se reactivó el cargo." : "Se desactivó el cargo.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return REDIRECT_CARGOS;
    }

    // ============================================================
    // CONFIGURACIÓN (Épica 5, C10)
    // ============================================================

    /**
     * Muestra los parámetros globales y su historial de cambios.
     *
     * @param model modelo con los datos de la vista
     * @return vista {@code admin/admin-configuracion}
     */
    @GetMapping({"/configuracion", "/admin-configuracion.html"})
    public String configuration(Model model) {
        model.addAttribute("parametros", adminConfiguracionService.listar());
        model.addAttribute("historial", adminConfiguracionService.historialReciente());
        return "admin/admin-configuracion";
    }

    /**
     * Crea un parámetro global.
     *
     * @param clave clave del parámetro
     * @param descripcion descripción; puede ser {@code null} o vacía
     * @param valor valor ingresado
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección a la configuración
     */
    @PostMapping("/configuracion/crear")
    public String crearConfiguracion(@RequestParam String clave, @RequestParam(required = false) String descripcion,
                                      @RequestParam String valor,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminConfiguracionService.crear(clave, descripcion, valor, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se creó el parámetro.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/configuracion";
    }

    /**
     * Cambia el valor de un parámetro global.
     *
     * @param parametroId id del parámetro
     * @param valor valor ingresado
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección a la configuración
     */
    @PostMapping("/configuracion/editar")
    public String editarConfiguracion(@RequestParam Long parametroId, @RequestParam String valor,
                                       @AuthenticationPrincipal UsuarioDetails principal,
                                       RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminConfiguracionService.editar(parametroId, valor, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeOk", "Se actualizó el parámetro.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/configuracion";
    }

    // ============================================================
    // AUDITORÍA (Épica 5, solo lectura)
    // ============================================================

    /**
     * Muestra el log de auditoría, filtrado y paginado.
     *
     * @param texto nombre o correo a buscar; {@code null} para no filtrar
     * @param rol etiqueta del rol; en los listados, {@code null} para no filtrar
     * @param accion código de la acción auditada; {@code null} o {@code "all"} para no filtrar
     * @param desde fecha inicial del rango; {@code null} para no limitar
     * @param hasta fecha final del rango; {@code null} para no limitar
     * @param pagina número de página, desde 1
     * @param model modelo con los datos de la vista
     * @return vista {@code admin/admin-auditoria}
     */
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

    /**
     * Descarga en CSV (UTF-8 con BOM, para que Excel muestre las tildes) las acciones que cumplen los filtros.
     *
     * @param texto nombre o correo a buscar; {@code null} para no filtrar
     * @param rol etiqueta del rol; en los listados, {@code null} para no filtrar
     * @param accion código de la acción auditada; {@code null} o {@code "all"} para no filtrar
     * @param desde fecha inicial del rango; {@code null} para no limitar
     * @param hasta fecha final del rango; {@code null} para no limitar
     * @param response respuesta HTTP
     * @throws IOException si falla la escritura de la respuesta
     */
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

        StringBuilder csv = new StringBuilder("Fecha y hora,Usuario,Rol,Tipo de accion,Detalle,IP\n");
        for (AdminAuditoriaService.LogFila log : logs) {
            csv.append(csvEscapar(log.fecha())).append(',')
                    .append(csvEscapar(log.usuarioNombre())).append(',')
                    .append(csvEscapar(log.rolEtiqueta())).append(',')
                    .append(csvEscapar(log.accionEtiqueta())).append(',')
                    .append(csvEscapar(log.detalle())).append(',')
                    .append(csvEscapar(log.ip())).append('\n');
        }
        out.write(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * Descarga en Excel las acciones que cumplen los filtros.
     *
     * @param texto nombre o correo a buscar; {@code null} para no filtrar
     * @param rol etiqueta del rol; en los listados, {@code null} para no filtrar
     * @param accion código de la acción auditada; {@code null} o {@code "all"} para no filtrar
     * @param desde fecha inicial del rango; {@code null} para no limitar
     * @param hasta fecha final del rango; {@code null} para no limitar
     * @param response respuesta HTTP
     * @throws IOException si falla la escritura de la respuesta
     */
    @GetMapping("/auditoria/exportar-excel")
    public void exportarAuditoriaExcel(@RequestParam(required = false) String texto,
                                       @RequestParam(required = false) String rol,
                                       @RequestParam(required = false) String accion,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                       HttpServletResponse response) throws IOException {
        List<AdminAuditoriaService.LogFila> logs = adminAuditoriaService.listar(
                new AdminAuditoriaService.FiltrosAuditoria(texto, rol, accion, desde, hasta));

        List<List<Object>> filas = new java.util.ArrayList<>();
        for (AdminAuditoriaService.LogFila log : logs) {
            filas.add(java.util.Arrays.<Object>asList(log.fecha(), log.usuarioNombre(), log.rolEtiqueta(),
                    log.accionEtiqueta(), log.detalle(), log.ip()));
        }
        List<com.pucp.skillb_ia.util.ReporteExcel.Hoja> hojas = new java.util.ArrayList<>();
        hojas.add(new com.pucp.skillb_ia.util.ReporteExcel.Hoja("Auditoria",
                List.of("Fecha y hora", "Usuario", "Rol", "Tipo de acción", "Detalle", "IP"), filas));

        java.util.Map<String, String> datos = new java.util.LinkedHashMap<>();
        datos.put("Registros", String.valueOf(logs.size()));
        datos.put("Desde", desde == null ? "Sin límite" : desde.toString());
        datos.put("Hasta", hasta == null ? "Sin límite" : hasta.toString());
        datos.put("Fecha de generación", com.pucp.skillb_ia.util.ReportePdf.ahora());

        byte[] excel = com.pucp.skillb_ia.util.ReporteExcel.crear("Log de auditoría", datos, hojas);

        response.setContentType(com.pucp.skillb_ia.util.ReporteExcel.TIPO_CONTENIDO);
        response.setHeader("Content-Disposition", "attachment; filename=\"auditoria.xlsx\"");
        response.getOutputStream().write(excel);
        response.getOutputStream().flush();
    }


    /**
     * Descarga en PDF las acciones que cumplen los filtros.
     *
     * @param texto nombre o correo a buscar; {@code null} para no filtrar
     * @param rol etiqueta del rol; en los listados, {@code null} para no filtrar
     * @param accion código de la acción auditada; {@code null} o {@code "all"} para no filtrar
     * @param desde fecha inicial del rango; {@code null} para no limitar
     * @param hasta fecha final del rango; {@code null} para no limitar
     * @param response respuesta HTTP
     * @throws IOException si falla la escritura de la respuesta
     */
    @GetMapping("/auditoria/exportar-pdf")
    public void exportarAuditoriaPdf(@RequestParam(required = false) String texto,
                                     @RequestParam(required = false) String rol,
                                     @RequestParam(required = false) String accion,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                     HttpServletResponse response) throws IOException {
        List<AdminAuditoriaService.LogFila> logs = adminAuditoriaService.listar(
                new AdminAuditoriaService.FiltrosAuditoria(texto, rol, accion, desde, hasta));

        List<List<Object>> filas = new java.util.ArrayList<>();
        for (AdminAuditoriaService.LogFila log : logs) {
            filas.add(java.util.Arrays.<Object>asList(log.fecha(), log.usuarioNombre(), log.rolEtiqueta(),
                    log.accionEtiqueta(), log.detalle(), log.ip()));
        }
        List<com.pucp.skillb_ia.util.ReporteExcel.Hoja> secciones = new java.util.ArrayList<>();
        secciones.add(new com.pucp.skillb_ia.util.ReporteExcel.Hoja("Registro de auditoría",
                List.of("Fecha y hora", "Usuario", "Rol", "Tipo de acción", "Detalle", "IP"), filas));

        java.util.Map<String, String> datos = new java.util.LinkedHashMap<>();
        datos.put("Registros", String.valueOf(logs.size()));
        datos.put("Desde", desde == null ? "Sin límite" : desde.toString());
        datos.put("Hasta", hasta == null ? "Sin límite" : hasta.toString());
        datos.put("Fecha de generación", com.pucp.skillb_ia.util.ReportePdf.ahora());

        byte[] pdf = com.pucp.skillb_ia.util.ReportePdf.crear("Log de auditoría",
                "SkillBridge AI — Panel del Administrador", datos, secciones);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"auditoria.pdf\"");
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }

    // ============================================================
    // REPORTES: "Usuarios y accesos" (por mes) y "Actividad de auditoría" (por rango de fechas)
    // ============================================================

    /**
     * Muestra los reportes "Usuarios y accesos" y "Actividad de auditoría". Con un rango inválido avisa y
     * muestra el mes actual.
     *
     * @param tab pestaña que se muestra
     * @param mes mes del reporte (formato yyyy-MM); si no es válido se usa el actual
     * @param desde fecha inicial del rango; {@code null} para no limitar
     * @param hasta fecha final del rango; {@code null} para no limitar
     * @param model modelo con los datos de la vista
     * @return vista {@code admin/admin-reportes}
     */
    @GetMapping({"/reportes", "/admin-reportes.html"})
    public String reportes(@RequestParam(required = false) String tab,
                           @RequestParam(required = false) String mes,
                           @RequestParam(required = false) String desde,
                           @RequestParam(required = false) String hasta,
                           Model model) {
        String pestana = "auditoria".equals(tab) ? "auditoria" : "usuarios";
        model.addAttribute("tab", pestana);

        java.time.YearMonth periodo = com.pucp.skillb_ia.util.Periodo.leer(mes);
        model.addAttribute("mes", periodo.toString());
        model.addAttribute("reporteUsuarios", adminReporteService.usuarios(periodo));

        LocalDate[] rango = leerRango(desde, hasta);
        model.addAttribute("desde", rango[0]);
        model.addAttribute("hasta", rango[1]);
        try {
            model.addAttribute("reporteAuditoria", adminReporteService.auditoria(rango[0], rango[1]));
        } catch (IllegalArgumentException e) {
            // Rango inválido (invertido o mayor a un año): se avisa y se muestra el mes actual.
            model.addAttribute("errorRango", e.getMessage());
            java.time.YearMonth actual = java.time.YearMonth.now();
            model.addAttribute("desde", actual.atDay(1));
            model.addAttribute("hasta", LocalDate.now());
            model.addAttribute("reporteAuditoria", adminReporteService.auditoria(actual.atDay(1), LocalDate.now()));
        }
        model.addAttribute("hoy", LocalDate.now());
        return "admin/admin-reportes";
    }

    /**
     * Descarga en Excel el reporte de la pestaña indicada.
     *
     * @param tab pestaña que se muestra
     * @param mes mes del reporte (formato yyyy-MM); si no es válido se usa el actual
     * @param desde fecha inicial del rango; {@code null} para no limitar
     * @param hasta fecha final del rango; {@code null} para no limitar
     * @return archivo .xlsx
     */
    @GetMapping("/reportes/excel")
    public org.springframework.http.ResponseEntity<byte[]> reportesExcel(@RequestParam(required = false) String tab,
                                                                         @RequestParam(required = false) String mes,
                                                                         @RequestParam(required = false) String desde,
                                                                         @RequestParam(required = false) String hasta) {
        byte[] contenido;
        String archivo;
        if ("auditoria".equals(tab)) {
            LocalDate[] rango = leerRango(desde, hasta);
            contenido = adminReporteService.excelAuditoria(adminReporteService.auditoria(rango[0], rango[1]));
            archivo = "reporte-auditoria-" + rango[0] + "-a-" + rango[1] + ".xlsx";
        } else {
            java.time.YearMonth periodo = com.pucp.skillb_ia.util.Periodo.leer(mes);
            contenido = adminReporteService.excelUsuarios(adminReporteService.usuarios(periodo));
            archivo = "reporte-usuarios-" + periodo + ".xlsx";
        }
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + archivo + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(com.pucp.skillb_ia.util.ReporteExcel.TIPO_CONTENIDO))
                .body(contenido);
    }


    /**
     * Descarga en PDF el reporte de la pestaña indicada.
     *
     * @param tab pestaña que se muestra
     * @param mes mes del reporte (formato yyyy-MM); si no es válido se usa el actual
     * @param desde fecha inicial del rango; {@code null} para no limitar
     * @param hasta fecha final del rango; {@code null} para no limitar
     * @return archivo .pdf
     */
    @GetMapping("/reportes/pdf")
    public org.springframework.http.ResponseEntity<byte[]> reportesPdf(@RequestParam(required = false) String tab,
                                                                       @RequestParam(required = false) String mes,
                                                                       @RequestParam(required = false) String desde,
                                                                       @RequestParam(required = false) String hasta) {
        byte[] contenido;
        String archivo;
        if ("auditoria".equals(tab)) {
            LocalDate[] rango = leerRango(desde, hasta);
            contenido = adminReporteService.pdfAuditoria(adminReporteService.auditoria(rango[0], rango[1]));
            archivo = "reporte-auditoria-" + rango[0] + "-a-" + rango[1] + ".pdf";
        } else {
            java.time.YearMonth periodo = com.pucp.skillb_ia.util.Periodo.leer(mes);
            contenido = adminReporteService.pdfUsuarios(adminReporteService.usuarios(periodo));
            archivo = "reporte-usuarios-" + periodo + ".pdf";
        }
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + archivo + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(contenido);
    }

    /**
     * Interpreta las fechas del reporte de auditoría.
     * <p>
     * Rango del reporte de auditoría: por defecto, del 1 del mes actual a hoy. Una fecha mal escrita usa el valor
     * por defecto (el formulario usa input type="date", así que solo pasa si se edita la URL).
     *
     * @param desde fecha inicial del rango; {@code null} para no limitar
     * @param hasta fecha final del rango; {@code null} para no limitar
     * @return arreglo con la fecha inicial y la final
     */
    private LocalDate[] leerRango(String desde, String hasta) {
        LocalDate fin = LocalDate.now();
        LocalDate inicio = fin.withDayOfMonth(1);
        try {
            if (hasta != null && !hasta.isBlank()) fin = LocalDate.parse(hasta.trim());
        } catch (java.time.format.DateTimeParseException ignorado) {
            // se mantiene hoy
        }
        try {
            if (desde != null && !desde.isBlank()) inicio = LocalDate.parse(desde.trim());
        } catch (java.time.format.DateTimeParseException ignorado) {
            // se mantiene el 1 del mes
        }
        return new LocalDate[]{inicio, fin};
    }

    // ============================================================
    // PERFIL
    // ============================================================

    /**
     * Muestra el perfil del Administrador.
     *
     * @param principal usuario con la sesión iniciada
     * @param model modelo con los datos de la vista
     * @return vista {@code admin/admin-perfil}
     */
    @GetMapping({"/perfil", "/admin-perfil.html"})
    public String perfil(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        completarModeloPerfil(model, principal);
        return "admin/admin-perfil";
    }

    /**
     * Completa lo que necesita la vista del perfil y sincroniza el estado del DNI.
     * <p>
     * Los dos formularios de la pantalla (teléfono y contraseña) necesitan su objeto en el modelo aunque se haya
     * enviado solo uno; el que falló conserva lo escrito y sus errores.
     *
     * @param model modelo con los datos de la vista
     * @param principal usuario con la sesión iniciada
     */
    private void completarModeloPerfil(Model model, UsuarioDetails principal) {
        // El DNI puede haberse verificado o quitado en segundo plano (DniReintentoScheduler).
        if (principal != null) {
            dniVerificacionService.sincronizar(principal.getUsuario());
        }
        if (!model.containsAttribute("telefonoPerfilForm")) {
            model.addAttribute("telefonoPerfilForm", new TelefonoPerfilForm(principal.getUsuario().getTelefono()));
        }
        if (!model.containsAttribute("passwordPerfilForm")) {
            model.addAttribute("passwordPerfilForm", new PasswordPerfilForm());
        }
    }

    /**
     * Actualiza el teléfono del Administrador.
     *
     * @param form datos del formulario
     * @param result errores de validación del formulario
     * @param model modelo con los datos de la vista
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al perfil, o la misma vista si hay errores
     */
    @PostMapping("/perfil/telefono")
    public String actualizarTelefonoAdmin(@Valid @ModelAttribute("telefonoPerfilForm") TelefonoPerfilForm form,
                                           BindingResult result, Model model,
                                           @AuthenticationPrincipal UsuarioDetails principal,
                                           RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (result.hasErrors()) {
            completarModeloPerfil(model, principal);
            return "admin/admin-perfil";
        }
        try {
            adminPerfilService.actualizarTelefono(form.getTelefono(), principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Teléfono actualizado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/perfil";
    }

    /**
     * Cambia la foto de perfil del Administrador.
     *
     * @param foto imagen subida
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al perfil
     */
    @PostMapping("/perfil/foto")
    public String actualizarFotoAdmin(@RequestParam("foto") MultipartFile foto,
                                       @AuthenticationPrincipal UsuarioDetails principal,
                                       RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            adminPerfilService.actualizarFoto(foto, principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Foto de perfil actualizada.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/perfil";
    }

    /**
     * Cambia la contraseña del Administrador, previa comprobación de la actual.
     *
     * @param form datos del formulario
     * @param result errores de validación del formulario
     * @param model modelo con los datos de la vista
     * @param principal usuario con la sesión iniciada
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al perfil, o la misma vista si hay errores
     */
    @PostMapping("/perfil/password")
    public String actualizarPasswordAdmin(@Valid @ModelAttribute("passwordPerfilForm") PasswordPerfilForm form,
                                           BindingResult result, Model model,
                                           @AuthenticationPrincipal UsuarioDetails principal,
                                           RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (result.hasErrors()) {
            completarModeloPerfil(model, principal);
            return "admin/admin-perfil";
        }
        try {
            adminPerfilService.actualizarPassword(form.getPasswordActual(), form.getPasswordNueva(), principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Contraseña actualizada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/perfil";
    }

    /**
     * Devuelve el mensaje del primer error de conversión del formulario.
     * <p>
     * Valor que no se pudo convertir (p. ej. texto en un campo numérico): mensaje de messages.properties.
     *
     * @param resultado resultado de enlazar el formulario
     * @return mensaje para el usuario
     */
    private String primerErrorDeBinding(BindingResult resultado) {
        var error = resultado.getFieldError();
        return error == null ? "Revisa los datos ingresados."
                : messageSource.getMessage(error, org.springframework.context.i18n.LocaleContextHolder.getLocale());
    }

    /**
     * Encierra un valor entre comillas y duplica las que contenga, para el CSV.
     *
     * @param valor valor ingresado
     * @return texto listo para el CSV; vacío si es {@code null}
     */
    private String csvEscapar(String valor) {
        if (valor == null) return "";
        return "\"" + valor.replace("\"", "\"\"") + "\"";
    }

    // ================== CURSOS ==================
    /**
     * Muestra el catálogo de cursos, separados en vigentes y caducados.
     *
     * @param paginaVigentes página de la tabla de cursos en curso
     * @param paginaFinalizados página de la tabla de cursos caducados
     * @param model modelo con los datos de la vista
     * @return vista {@code admin/admin-cursos}
     */
    @GetMapping({"/cursos", "/admin-cursos.html"})
    public String cursos(@RequestParam(required = false) Integer paginaVigentes,
                         @RequestParam(required = false) Integer paginaFinalizados,
                         Model model) {
        Page<Curso> vigentes = com.pucp.skillb_ia.util.Paginador.paginar(adminCursoService.listarVigentes(), paginaVigentes);
        Page<Curso> finalizados = com.pucp.skillb_ia.util.Paginador.paginar(adminCursoService.listarFinalizados(), paginaFinalizados);
        model.addAttribute("cursos", adminCursoService.listarTodos());
        model.addAttribute("paginaVigentes", vigentes);
        model.addAttribute("paginaFinalizados", finalizados);
        model.addAttribute("cursosVigentes", vigentes.getContent());
        model.addAttribute("cursosFinalizados", finalizados.getContent());
        model.addAttribute("modalidades", com.pucp.skillb_ia.model.enums.ModalidadCurso.values());
        return "admin/admin-cursos";
    }

    /**
     * Registra un curso.
     *
     * @param curso datos del curso
     * @param bindingResult errores al convertir los datos del formulario
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al catálogo de cursos
     */
    @PostMapping("/cursos/crear")
    public String crearCurso(@ModelAttribute Curso curso, BindingResult bindingResult,
                             @AuthenticationPrincipal UsuarioDetails principal,
                             RedirectAttributes ra, HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("mensajeError", primerErrorDeBinding(bindingResult));
            return "redirect:/admin/cursos";
        }
        try {
            adminCursoService.crear(curso, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "Se creó el curso exitosamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/cursos";
    }

    /**
     * Guarda los cambios de un curso.
     *
     * @param cursoId id del curso
     * @param curso datos del curso
     * @param bindingResult errores al convertir los datos del formulario
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al catálogo de cursos
     */
    @PostMapping("/cursos/editar")
    public String editarCurso(@RequestParam("cursoId") Long cursoId,
                              @ModelAttribute Curso curso, BindingResult bindingResult,
                              @AuthenticationPrincipal UsuarioDetails principal,
                              RedirectAttributes ra, HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("mensajeError", primerErrorDeBinding(bindingResult));
            return "redirect:/admin/cursos";
        }
        try {
            adminCursoService.editar(cursoId, curso, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "Se guardaron los cambios del curso.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/cursos";
    }

    /**
     * Activa o desactiva un curso.
     *
     * @param cursoId id del curso
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al catálogo de cursos
     */
    @PostMapping("/cursos/alternar")
    public String alternarEstadoCurso(@RequestParam("cursoId") Long cursoId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes ra, HttpServletRequest request) {
        try {
            adminCursoService.alternarEstado(cursoId, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "El estado del curso ha sido actualizado.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/cursos";
    }

    // ============================================================
    // CV Y EXPERIENCIA PROFESIONAL DE COLABORADORES
    // ============================================================

    /**
     * Muestra los pre-registros en tres pestañas: pendientes, aprobados y rechazados.
     *
     * @param tab pestaña que se muestra
     * @param paginaPendientes página de la pestaña de pendientes
     * @param paginaRevisados página de la pestaña de aprobados
     * @param paginaRechazados página de la pestaña de rechazados
     * @param model modelo con los datos de la vista
     * @return vista {@code admin/admin-experiencia}
     */
    @GetMapping({"/experiencia", "/admin-experiencia.html"})
    public String experiencia(@RequestParam(required = false) String tab,
                              @RequestParam(required = false) Integer paginaPendientes,
                              @RequestParam(required = false) Integer paginaRevisados,
                              @RequestParam(required = false) Integer paginaRechazados,
                              Model model) {
        // Paginación en el servidor (tema 5.7): una página por pestaña.
        Page<AdminUsuarioService.CvFila> pendientes = com.pucp.skillb_ia.util.Paginador.paginar(adminUsuarioService.listarCvsPendientes(), paginaPendientes);
        Page<AdminUsuarioService.CvFila> revisados = com.pucp.skillb_ia.util.Paginador.paginar(adminUsuarioService.listarCvsRevisados(), paginaRevisados);
        Page<AdminUsuarioService.CvFila> rechazados = com.pucp.skillb_ia.util.Paginador.paginar(adminUsuarioService.listarCvsRechazados(), paginaRechazados);
        model.addAttribute("paginaPendientes", pendientes);
        model.addAttribute("paginaRevisados", revisados);
        model.addAttribute("paginaRechazados", rechazados);
        model.addAttribute("cvsPendientes", pendientes.getContent());
        model.addAttribute("cvsRevisados", revisados.getContent());
        model.addAttribute("cvsRechazados", rechazados.getContent());
        model.addAttribute("tab", "revisados".equals(tab) || "rechazados".equals(tab) ? tab : "pendientes");
        return "admin/admin-experiencia";
    }

    /**
     * Muestra el pre-registro de un colaborador con su experiencia y, solo para lectura, su formación y sus
     * certificados.
     * <p>
     * Ojo: el path variable NO se llama "id" a propósito — {@literal @}ModelAttribute ExperienciaProfesional
     * también tiene un campo "id", y Spring Data Binder pisa ese campo con cualquier atributo implícito del
     * modelo que se llame igual (incluidos los {@literal @}PathVariable), hacendo que el repository intente
     * actualizar una fila ajena en vez de crear una nueva.
     *
     * @param colaboradorId id del colaborador
     * @param model modelo con los datos de la vista
     * @param ra mensajes que se muestran después de redirigir
     * @return vista {@code admin/admin-experiencia-detalle}, o redirección a la lista si no existe
     */
    @GetMapping("/experiencia/{colaboradorId}")
    public String experienciaDetalle(@PathVariable Long colaboradorId, Model model, RedirectAttributes ra) {
        Usuario colaborador;
        try {
            colaborador = adminUsuarioService.obtenerUsuario(colaboradorId);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
            return "redirect:/admin/experiencia";
        }
        model.addAttribute("colaborador", colaborador);
        model.addAttribute("experiencias", colaboradorPerfilService.listarExperienciaProfesional(colaborador));
        // Solo lectura, para compararlos con el CV: los valida el Resource Manager.
        model.addAttribute("estudios", colaboradorPerfilService.listarEducacion(colaborador));
        model.addAttribute("habilidades", colaboradorPerfilService.listarHabilidades(colaborador));
        model.addAttribute("certificadosPorHabilidad", colaboradorPerfilService.mapaCertificadosPorHabilidad(colaborador));
        return "admin/admin-experiencia-detalle";
    }

    /**
     * Agrega al colaborador una experiencia que figura en su CV.
     *
     * @param colaboradorId id del colaborador
     * @param exp datos de la experiencia profesional
     * @param bindingResult errores al convertir los datos del formulario
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al detalle del pre-registro
     */
    @PostMapping("/experiencia/{colaboradorId}/agregar")
    public String agregarExperiencia(@PathVariable Long colaboradorId,
                                     @ModelAttribute ExperienciaProfesional exp, BindingResult bindingResult,
                                     RedirectAttributes ra, HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("mensajeError", primerErrorDeBinding(bindingResult));
            return "redirect:/admin/experiencia/" + colaboradorId;
        }
        try {
            Usuario colaborador = adminUsuarioService.obtenerUsuario(colaboradorId);
            colaboradorPerfilService.agregarExperiencia(colaborador, exp);
            ra.addFlashAttribute("mensajeOk", "Se agregó la experiencia profesional.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/experiencia/" + colaboradorId;
    }

    /**
     * Descarta una experiencia del colaborador.
     * <p>
     * El Admin descarta una experiencia que no pudo verificar con el CV.
     *
     * @param colaboradorId id del colaborador
     * @param experienciaId id de la experiencia profesional
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @return redirección al detalle del pre-registro
     */
    @PostMapping("/experiencia/{colaboradorId}/eliminar/{experienciaId}")
    public String eliminarExperiencia(@PathVariable Long colaboradorId, @PathVariable Long experienciaId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes ra) {
        try {
            Usuario colaborador = adminUsuarioService.obtenerUsuario(colaboradorId);
            colaboradorPerfilService.eliminarExperiencia(colaborador, experienciaId, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "Se eliminó la experiencia profesional.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/admin/experiencia/" + colaboradorId;
    }

    /**
     * Aprueba el pre-registro o el CV de reemplazo del colaborador.
     *
     * @param colaboradorId id del colaborador
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección a la lista, o al detalle si no se pudo aprobar
     */
    @PostMapping("/experiencia/{colaboradorId}/revisar")
    public String aprobarRegistro(@PathVariable Long colaboradorId,
                                  @AuthenticationPrincipal UsuarioDetails principal,
                                  RedirectAttributes ra, HttpServletRequest request) {
        try {
            adminUsuarioService.aprobarRegistro(colaboradorId, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "Se aprobó el registro del colaborador.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", mensajeSeguro(e));
            return "redirect:/admin/experiencia/" + colaboradorId;
        }
        return "redirect:/admin/experiencia";
    }

    /**
     * Devuelve el pre-registro al colaborador con las observaciones.
     *
     * @param colaboradorId id del colaborador
     * @param motivo motivo del rechazo, de hasta 500 caracteres
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección a la lista, o al detalle si falta el motivo
     */
    @PostMapping("/experiencia/{colaboradorId}/rechazar")
    public String rechazarRegistro(@PathVariable Long colaboradorId,
                                   @RequestParam("motivo") String motivo,
                                   @AuthenticationPrincipal UsuarioDetails principal,
                                   RedirectAttributes ra, HttpServletRequest request) {
        try {
            adminUsuarioService.rechazarRegistro(colaboradorId, motivo, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "Se devolvió el registro al colaborador con tus observaciones.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", mensajeSeguro(e));
            return "redirect:/admin/experiencia/" + colaboradorId;
        }
        return "redirect:/admin/experiencia";
    }

    // ═══════════════════════════════════════════════════════════
    // FOROS DE COMUNIDAD
    // ═══════════════════════════════════════════════════════════

    /**
     * Muestra los foros que administra: los de comunidad y los de anuncios.
     *
     * @param model modelo con los datos de la vista
     * @return vista {@code admin/admin-foros}
     */
    @GetMapping("/foros")
    public String foros(Model model) {
        model.addAttribute("foros", adminForoService.listarForosComunidad());
        return "admin/admin-foros";
    }

    /**
     * Crea un foro de comunidad o de anuncios.
     *
     * @param nombre nombre ingresado
     * @param esPublico {@code true} para un foro de comunidad, {@code false} para uno de anuncios
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección a la lista de foros
     */
    @PostMapping("/foros/crear")
    public String crearForo(@RequestParam String nombre,
                            @RequestParam(defaultValue = "true") boolean esPublico,
                            @AuthenticationPrincipal UsuarioDetails principal,
                            RedirectAttributes ra, HttpServletRequest request) {
        try {
            adminForoService.crearForo(nombre.trim(), esPublico, principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            ra.addFlashAttribute("success", "Foro \"" + nombre + "\" creado exitosamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/admin/foros";
    }

    /**
     * Elimina un foro con todo su contenido.
     *
     * @param id id del registro
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección a la lista de foros
     */
    @PostMapping("/foros/{id}/eliminar")
    public String eliminarForo(@PathVariable Long id, @AuthenticationPrincipal UsuarioDetails principal,
                               RedirectAttributes ra, HttpServletRequest request) {
        try {
            adminForoService.eliminarForo(id, principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            ra.addFlashAttribute("success", "Foro eliminado correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudo eliminar el foro. " + mensajeSeguro(e));
        }
        return "redirect:/admin/foros";
    }

    /**
     * Muestra las publicaciones de un foro.
     *
     * @param id id del registro
     * @param ordenar orden de las publicaciones: {@code "fecha"} o {@code "votos"}
     * @param principal usuario con la sesión iniciada
     * @param model modelo con los datos de la vista
     * @return vista {@code admin/admin-foro-detalle}
     */
    @GetMapping("/foros/{id}/publicaciones")
    public String foroDetalle(@PathVariable Long id,
                              @RequestParam(value = "ordenar", defaultValue = "fecha") String ordenar,
                              @AuthenticationPrincipal UsuarioDetails principal, Model model) {
        String orden = com.pucp.skillb_ia.service.ForoDetalleService.normalizarOrden(ordenar);
        model.addAttribute("foro", adminForoService.obtenerForo(id));
        model.addAttribute("publicaciones", adminForoService.obtenerDetalle(id, principal.getUsuario(), orden));
        model.addAttribute("ordenActual", orden);
        return "admin/admin-foro-detalle";
    }

    /**
     * Publica en un foro.
     *
     * @param id id del registro
     * @param titulo título de la publicación
     * @param contenido contenido escrito por el usuario
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al detalle del foro
     */
    @PostMapping("/foros/{id}/publicar")
    public String publicarEnForo(@PathVariable Long id,
                                 @RequestParam String titulo,
                                 @RequestParam String contenido,
                                 @AuthenticationPrincipal UsuarioDetails principal,
                                 RedirectAttributes ra, HttpServletRequest request) {
        try {
            adminForoService.publicar(id, titulo, contenido, principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            ra.addFlashAttribute("success", "Anuncio publicado correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/admin/foros/" + id + "/publicaciones";
    }

    /**
     * Elimina cualquier publicación del foro.
     *
     * @param pubId id de la publicación
     * @param foroId id del foro
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al detalle del foro
     */
    @PostMapping("/foros/publicacion/{pubId}/eliminar")
    public String eliminarPublicacion(@PathVariable Long pubId,
                                      @RequestParam Long foroId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes ra, HttpServletRequest request) {
        try {
            adminForoService.eliminarPublicacion(pubId, principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            ra.addFlashAttribute("success", "Publicación eliminada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/admin/foros/" + foroId + "/publicaciones";
    }

    /**
     * Responde una publicación; en los foros de anuncios no se permite.
     *
     * @param publicacionId id de la publicación
     * @param foroId id del foro
     * @param contenido contenido escrito por el usuario
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al detalle del foro
     */
    @PostMapping("/foros/responder")
    public String responderForo(@RequestParam("publicacionId") Long publicacionId,
                                @RequestParam(value = "foroId", required = false) Long foroId,
                                @RequestParam("contenido") String contenido,
                                @AuthenticationPrincipal UsuarioDetails principal,
                                RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmForoService.responder(publicacionId, contenido, principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            ra.addFlashAttribute("success", "Respuesta publicada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/admin/foros/" + foroId + "/publicaciones";
    }

    /**
     * Marca o desmarca una respuesta como solución.
     *
     * @param respuestaId id de la respuesta
     * @param foroId id del foro
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al detalle del foro
     */
    @PostMapping("/foros/respuesta/{id}/marcar-solucion")
    public String marcarSolucionForo(@PathVariable("id") Long respuestaId,
                                     @RequestParam(value = "foroId", required = false) Long foroId,
                                     @AuthenticationPrincipal UsuarioDetails principal,
                                     RedirectAttributes ra, HttpServletRequest request) {
        try {
            adminForoService.alternarSolucion(principal.getUsuario(), respuestaId);
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/admin/foros/" + foroId + "/publicaciones";
    }

    /**
     * Da o quita "me gusta" a una publicación.
     *
     * @param publicacionId id de la publicación
     * @param foroId id del foro
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al detalle del foro
     */
    @PostMapping("/foros/publicacion/{publicacionId}/like")
    public String likePublicacion(@PathVariable Long publicacionId,
                                  @RequestParam(value = "foroId", required = false) Long foroId,
                                  @AuthenticationPrincipal UsuarioDetails principal,
                                  RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmForoService.alternarLikePublicacion(principal.getUsuario(), publicacionId);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/admin/foros/" + foroId + "/publicaciones";
    }

    /**
     * Da o quita "me gusta" a una respuesta.
     *
     * @param respuestaId id de la respuesta
     * @param foroId id del foro
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al detalle del foro
     */
    @PostMapping("/foros/respuesta/{respuestaId}/like")
    public String likeRespuesta(@PathVariable Long respuestaId,
                                @RequestParam(value = "foroId", required = false) Long foroId,
                                @AuthenticationPrincipal UsuarioDetails principal,
                                RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmForoService.alternarLikeRespuesta(principal.getUsuario(), respuestaId);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/admin/foros/" + foroId + "/publicaciones";
    }

    /**
     * Elimina cualquier respuesta del foro.
     *
     * @param respuestaId id de la respuesta
     * @param foroId id del foro
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @param request petición HTTP
     * @return redirección al detalle del foro
     */
    @PostMapping("/foros/respuesta/{respuestaId}/eliminar")
    public String eliminarRespuestaForo(@PathVariable Long respuestaId,
                                        @RequestParam(value = "foroId", required = false) Long foroId,
                                        @AuthenticationPrincipal UsuarioDetails principal,
                                        RedirectAttributes ra, HttpServletRequest request) {
        try {
            adminForoService.eliminarRespuesta(respuestaId, principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            ra.addFlashAttribute("success", "Respuesta eliminada.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/admin/foros/" + foroId + "/publicaciones";
    }
}
