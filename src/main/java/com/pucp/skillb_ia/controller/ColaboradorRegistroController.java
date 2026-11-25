package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import static com.pucp.skillb_ia.controller.GlobalExceptionHandler.mensajeSeguro;

/**
 * Pantalla "Registro en revisión" del pre-registro (rutas bajo {@code /colaborador/registro}).
 * <p>
 * Muestra al colaborador el estado de su registro mientras el Administrador lo revisa, y le deja
 * subir un CV nuevo, por ejemplo cuando el Administrador rechazó el anterior.
 *
 * @see com.pucp.skillb_ia.security.RegistroPendienteInterceptor
 */
@Controller
@RequestMapping("/colaborador/registro")
public class ColaboradorRegistroController {

    private final UsuarioRepository usuarioRepository;
    private final ColaboradorPerfilService colaboradorPerfilService;

    /**
     * @param usuarioRepository acceso a usuarios (para recargar sus datos actualizados)
     * @param colaboradorPerfilService lógica del perfil; aquí se usa para subir el CV
     */
    public ColaboradorRegistroController(UsuarioRepository usuarioRepository,
                                         ColaboradorPerfilService colaboradorPerfilService) {
        this.usuarioRepository = usuarioRepository;
        this.colaboradorPerfilService = colaboradorPerfilService;
    }

    /**
     * Muestra la pantalla de registro en revisión ({@code GET /colaborador/registro}). Recarga el
     * usuario desde la base de datos para mostrar el estado actual del registro y del CV.
     *
     * @param principal usuario autenticado
     * @param model modelo para la vista
     * @return la vista {@code col/col-registro}
     */
    @GetMapping
    public String registro(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        model.addAttribute("usuario", recargar(principal));
        return "col/col-registro";
    }

    /**
     * Sube un CV nuevo ({@code POST /colaborador/registro/cv}). El CV queda pendiente de revisión
     * del Administrador. Cualquier error (archivo inválido, por ejemplo) se captura y se muestra
     * como mensaje, con {@code mensajeSeguro} para no exponer detalles internos.
     *
     * @param cv archivo PDF del CV
     * @param principal usuario autenticado
     * @param ra para los mensajes tras la redirección
     * @return redirección a {@code /colaborador/registro}
     * @see ColaboradorPerfilService#actualizarCv
     */
    @PostMapping("/cv")
    public String subirCv(@RequestParam("cv") MultipartFile cv,
                          @AuthenticationPrincipal UsuarioDetails principal,
                          RedirectAttributes ra) {
        try {
            colaboradorPerfilService.actualizarCv(recargar(principal), cv);
            ra.addFlashAttribute("mensajeOk", "Tu CV se envió. El Administrador lo revisará pronto.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/colaborador/registro";
    }

    /**
     * Vuelve a leer al usuario desde la base de datos, porque el que viene en la sesión puede
     * estar desactualizado (por ejemplo, tras un rechazo del CV).
     *
     * @param principal usuario autenticado
     * @return el usuario con sus datos actuales
     * @throws java.util.NoSuchElementException si el usuario ya no existe
     */
    private Usuario recargar(UsuarioDetails principal) {
        return usuarioRepository.findById(principal.getUsuario().getId()).orElseThrow();
    }
}
