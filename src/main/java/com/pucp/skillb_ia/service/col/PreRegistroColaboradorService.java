package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.auth.ActivarCuentaForm;
import com.pucp.skillb_ia.dto.auth.CertificadoPreRegistroForm;
import com.pucp.skillb_ia.dto.auth.ErrorPreRegistro;
import com.pucp.skillb_ia.dto.auth.EstudioPreRegistroForm;
import com.pucp.skillb_ia.dto.auth.ExperienciaForm;
import com.pucp.skillb_ia.model.ExperienciaProfesional;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Usuario;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Pre-registro del colaborador (se completa al activar la cuenta): valida y guarda su CV, experiencia
// profesional, certificados de habilidades y formación académica. Reutiliza los métodos que ya usa
// su perfil, así que lo cargado aparece solo en "Mi perfil" y el Admin/RM no tienen que digitarlo.
@Service
public class PreRegistroColaboradorService {

    private static final Logger log = LoggerFactory.getLogger(PreRegistroColaboradorService.class);
    private static final int MAX_FILAS = 10;
    private static final LocalDate FECHA_MINIMA = LocalDate.of(1950, 1, 1);
    private static final Pattern CAMPO_DE_FILA =
            Pattern.compile("(experiencias|certificados|estudios)\\[(\\d+)\\]\\.(\\w+)");

    private final ColaboradorPerfilService colaboradorPerfilService;
    private final PreRegistroArchivosService archivosPendientes;
    private final MessageSource mensajes;

    public PreRegistroColaboradorService(ColaboradorPerfilService colaboradorPerfilService,
                                         PreRegistroArchivosService archivosPendientes, MessageSource mensajes) {
        this.colaboradorPerfilService = colaboradorPerfilService;
        this.archivosPendientes = archivosPendientes;
        this.mensajes = mensajes;
    }

    // ============================================================
    // VALIDACIÓN (antes de crear la cuenta)
    // ============================================================

    // Solo se validan las filas que el usuario empezó a llenar; una fila en blanco se ignora.
    public void validar(ActivarCuentaForm form, BindingResult result, HttpSession session) {
        // El teléfono (9 dígitos, igual que el perfil del colaborador) ya lo validó el formulario.
        form.setCvGuardado(resolverArchivo(form.getCv(), form.getCvGuardado(), colaboradorPerfilService::validarCv,
                "cv", "cv.invalido", result, session));
        validarExperiencias(form.getExperiencias(), result);
        validarCertificados(form.getCertificados(), result, session);
        validarEstudios(form.getEstudios(), result, session);
    }

    // Un archivo válido se guarda de inmediato, aunque otros campos tengan errores: así no hay que volver a
    // adjuntarlo. Devuelve el id del archivo que vale para este campo, o null si todavía no hay ninguno.
    private String resolverArchivo(MultipartFile nuevo, String idGuardado, Consumer<MultipartFile> validador,
                                   String campo, String codigo, BindingResult result, HttpSession session) {
        boolean yaGuardado = idGuardado != null && archivosPendientes.archivos(session).containsKey(idGuardado);
        boolean hayNuevo = nuevo != null && !nuevo.isEmpty();
        if (!hayNuevo && yaGuardado) {
            return idGuardado;
        }
        try {
            validador.accept(hayNuevo ? nuevo : null);
            return archivosPendientes.guardar(nuevo, session).id();
        } catch (IllegalArgumentException e) {
            result.rejectValue(campo, codigo, e.getMessage());
        } catch (IOException e) {
            log.warn("No se pudo guardar un archivo temporal del pre-registro: {}", e.getMessage());
            result.rejectValue(campo, codigo, "No pudimos recibir este archivo. Adjúntalo de nuevo.");
        }
        return yaGuardado ? idGuardado : null;
    }

    private void validarExperiencias(List<ExperienciaForm> filas, BindingResult result) {
        if (excedeMaximo(filas.stream().filter(f -> !f.estaVacia()).count(), "experiencias", "experiencias", result)) {
            return;
        }
        for (int i = 0; i < filas.size(); i++) {
            ExperienciaForm f = filas.get(i);
            if (f.estaVacia()) continue;
            String campo = "experiencias[" + i + "].";
            if (vacio(f.getCargo())) {
                result.rejectValue(campo + "cargo", "exp.cargo", "El cargo es obligatorio.");
            } else if (f.getCargo().length() > 100) {
                result.rejectValue(campo + "cargo", "exp.cargo", "El cargo no puede superar los 100 caracteres.");
            }
            if (vacio(f.getEmpresa())) {
                result.rejectValue(campo + "empresa", "exp.empresa", "La empresa es obligatoria.");
            } else if (f.getEmpresa().length() > 150) {
                result.rejectValue(campo + "empresa", "exp.empresa", "La empresa no puede superar los 150 caracteres.");
            }
            if (vacio(f.getDescripcion())) {
                result.rejectValue(campo + "descripcion", "exp.descripcion", "Describe brevemente tus funciones.");
            } else if (f.getDescripcion().length() > 500) {
                result.rejectValue(campo + "descripcion", "exp.descripcion", "La descripción no puede superar los 500 caracteres.");
            }
            if (f.getFechaInicio() == null) {
                if (!result.hasFieldErrors(campo + "fechaInicio")) {
                    result.rejectValue(campo + "fechaInicio", "exp.inicio", "La fecha de inicio es obligatoria.");
                }
            } else if (f.getFechaInicio().isAfter(LocalDate.now())) {
                result.rejectValue(campo + "fechaInicio", "exp.inicio", "La fecha de inicio no puede ser futura.");
            } else if (f.getFechaInicio().isBefore(FECHA_MINIMA)) {
                result.rejectValue(campo + "fechaInicio", "exp.inicio", "Revisa el año de la fecha de inicio.");
            }
            if (!f.isActual()) {
                if (f.getFechaFin() == null) {
                    if (!result.hasFieldErrors(campo + "fechaFin")) {
                        result.rejectValue(campo + "fechaFin", "exp.fin", "Indica la fecha de fin o marca que trabajas ahí actualmente.");
                    }
                } else if (f.getFechaInicio() != null && f.getFechaFin().isBefore(f.getFechaInicio())) {
                    result.rejectValue(campo + "fechaFin", "exp.fin", "La fecha de fin no puede ser anterior a la de inicio.");
                } else if (f.getFechaFin().isAfter(LocalDate.now())) {
                    result.rejectValue(campo + "fechaFin", "exp.fin", "La fecha de fin no puede ser futura. Si sigues ahí, marca que trabajas actualmente.");
                }
            }
        }
    }

    private void validarCertificados(List<CertificadoPreRegistroForm> filas, BindingResult result, HttpSession session) {
        if (excedeMaximo(filas.stream().filter(f -> !f.estaVacia()).count(), "certificados", "certificados", result)) {
            return;
        }
        Set<Long> catalogo = colaboradorPerfilService.listarHabilidadesActivas().stream()
                .map(Habilidad::getId).collect(Collectors.toSet());
        Set<Long> yaElegidas = new HashSet<>();
        for (int i = 0; i < filas.size(); i++) {
            CertificadoPreRegistroForm f = filas.get(i);
            if (f.estaVacia()) continue;
            String campo = "certificados[" + i + "].";
            if (f.getHabilidadId() == null) {
                if (!result.hasFieldErrors(campo + "habilidadId")) {
                    result.rejectValue(campo + "habilidadId", "cert.habilidad", "Selecciona la habilidad.");
                }
            } else if (!catalogo.contains(f.getHabilidadId())) {
                result.rejectValue(campo + "habilidadId", "cert.habilidad", "La habilidad seleccionada no existe.");
            } else if (!yaElegidas.add(f.getHabilidadId())) {
                result.rejectValue(campo + "habilidadId", "cert.habilidad", "Ya agregaste un certificado para esta habilidad.");
            }
            if (f.getNivel() == null && !result.hasFieldErrors(campo + "nivel")) {
                result.rejectValue(campo + "nivel", "cert.nivel", "Selecciona tu nivel.");
            }
            f.setArchivoGuardado(resolverArchivo(f.getArchivo(), f.getArchivoGuardado(),
                    colaboradorPerfilService::validarArchivoCertificado, campo + "archivo", "cert.archivo", result, session));
        }
    }

    private void validarEstudios(List<EstudioPreRegistroForm> filas, BindingResult result, HttpSession session) {
        if (excedeMaximo(filas.stream().filter(f -> !f.estaVacia()).count(), "estudios", "estudios", result)) {
            return;
        }
        for (int i = 0; i < filas.size(); i++) {
            EstudioPreRegistroForm f = filas.get(i);
            if (f.estaVacia()) continue;
            String campo = "estudios[" + i + "].";
            if (vacio(f.getInstitucion())) {
                result.rejectValue(campo + "institucion", "est.institucion", "Indica la institución.");
            } else if (f.getInstitucion().length() > 150) {
                result.rejectValue(campo + "institucion", "est.institucion", "La institución no puede superar los 150 caracteres.");
            }
            if (vacio(f.getTitulo())) {
                result.rejectValue(campo + "titulo", "est.titulo", "Indica el título o carrera.");
            } else if (f.getTitulo().length() > 150) {
                result.rejectValue(campo + "titulo", "est.titulo", "El título no puede superar los 150 caracteres.");
            }
            if (f.getFechaInicio() == null) {
                if (!result.hasFieldErrors(campo + "fechaInicio")) {
                    result.rejectValue(campo + "fechaInicio", "est.inicio", "Indica la fecha de inicio.");
                }
            } else if (f.getFechaInicio().isBefore(FECHA_MINIMA)) {
                result.rejectValue(campo + "fechaInicio", "est.inicio", "Revisa el año de la fecha de inicio.");
            }
            if (f.getFechaFin() == null) {
                if (!result.hasFieldErrors(campo + "fechaFin")) {
                    result.rejectValue(campo + "fechaFin", "est.fin", "Indica la fecha de fin.");
                }
            } else if (f.getFechaInicio() != null && f.getFechaFin().isBefore(f.getFechaInicio())) {
                result.rejectValue(campo + "fechaFin", "est.fin", "La fecha de fin no puede ser anterior a la de inicio.");
            } else if (f.getFechaFin().isAfter(LocalDate.now())) {
                result.rejectValue(campo + "fechaFin", "est.fin", "La fecha de fin no puede ser futura.");
            }
            f.setArchivoGuardado(resolverArchivo(f.getArchivo(), f.getArchivoGuardado(),
                    colaboradorPerfilService::validarArchivoCertificado, campo + "archivo", "est.archivo", result, session));
        }
    }

    private boolean excedeMaximo(long usadas, String campo, String codigo, BindingResult result) {
        if (usadas > MAX_FILAS) {
            result.rejectValue(campo, codigo + ".demasiadas",
                    "Puedes registrar hasta " + MAX_FILAS + " filas en esta sección.");
            return true;
        }
        return false;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    // ============================================================
    // RESUMEN DE ERRORES (lista de la parte superior del formulario)
    // ============================================================

    // Un error por campo, ordenados por paso, con el nombre que ve el usuario ("Certificado 2 · Nivel").
    public List<ErrorPreRegistro> resumirErrores(BindingResult result) {
        Map<String, ErrorPreRegistro> porCampo = new LinkedHashMap<>();
        for (FieldError error : result.getFieldErrors()) {
            String campo = error.getField();
            String mensaje = mensajes.getMessage(error, LocaleContextHolder.getLocale());
            porCampo.putIfAbsent(campo, new ErrorPreRegistro(pasoDe(campo), etiquetaDe(campo), mensaje,
                    "passwordsCoinciden".equals(campo) ? "confirmarPassword" : campo));
        }
        return porCampo.values().stream().sorted(Comparator.comparingInt(ErrorPreRegistro::paso)).toList();
    }

    private int pasoDe(String campo) {
        if (campo.equals("cv") || campo.startsWith("experiencias")) return 2;
        if (campo.startsWith("certificados") || campo.startsWith("estudios")) return 3;
        if (campo.equals("nombreCompleto") || campo.equals("telefono") || campo.equals("descripcion")) return 1;
        return 4;
    }

    private String etiquetaDe(String campo) {
        Matcher fila = CAMPO_DE_FILA.matcher(campo);
        if (fila.matches()) {
            String seccion = switch (fila.group(1)) {
                case "experiencias" -> "Experiencia";
                case "certificados" -> "Certificado";
                default -> "Formación";
            };
            return seccion + " " + (Integer.parseInt(fila.group(2)) + 1) + " · " + etiquetaDeColumna(fila.group(3));
        }
        return switch (campo) {
            case "nombreCompleto" -> "Nombre completo";
            case "telefono" -> "Teléfono";
            case "descripcion" -> "Sobre ti";
            case "cv" -> "CV";
            case "experiencias" -> "Experiencia profesional";
            case "certificados" -> "Certificados";
            case "estudios" -> "Formación académica";
            case "password" -> "Contraseña";
            case "confirmarPassword", "passwordsCoinciden" -> "Confirmar contraseña";
            case "aceptaPolitica" -> "Política de datos";
            default -> campo;
        };
    }

    private String etiquetaDeColumna(String columna) {
        return switch (columna) {
            case "cargo" -> "Cargo";
            case "empresa" -> "Empresa";
            case "descripcion" -> "Funciones principales";
            case "fechaInicio" -> "Fecha de inicio";
            case "fechaFin" -> "Fecha de fin";
            case "habilidadId" -> "Habilidad";
            case "nivel" -> "Nivel";
            case "archivo" -> "Archivo";
            case "institucion" -> "Institución";
            case "titulo" -> "Título o carrera";
            default -> columna;
        };
    }

    // ============================================================
    // GUARDADO (la cuenta ya está creada)
    // ============================================================

    // Cada fila se guarda por separado: si una falla (p. ej. el almacenamiento), el resto sigue y el
    // colaborador puede completarla después desde su perfil.
    public void guardar(Usuario usuario, ActivarCuentaForm form, HttpSession session) {
        intentar(usuario, "CV", () -> colaboradorPerfilService.actualizarCv(
                usuario, archivosPendientes.abrir(form.getCvGuardado(), session)));

        for (ExperienciaForm f : form.getExperiencias()) {
            if (f.estaVacia()) continue;
            ExperienciaProfesional exp = new ExperienciaProfesional();
            exp.setCargo(f.getCargo().strip());
            exp.setEmpresa(f.getEmpresa().strip());
            exp.setDescripcion(f.getDescripcion().strip());
            exp.setFechaInicio(f.getFechaInicio());
            exp.setFechaFin(f.isActual() ? null : f.getFechaFin());
            exp.setActual(f.isActual());
            intentar(usuario, "experiencia", () -> colaboradorPerfilService.agregarExperiencia(usuario, exp));
        }

        for (CertificadoPreRegistroForm f : form.getCertificados()) {
            if (f.estaVacia()) continue;
            intentar(usuario, "certificado", () -> colaboradorPerfilService.agregarHabilidad(
                    usuario, f.getHabilidadId(), null, null, f.getNivel(),
                    archivosPendientes.abrir(f.getArchivoGuardado(), session)));
        }

        for (EstudioPreRegistroForm f : form.getEstudios()) {
            if (f.estaVacia()) continue;
            intentar(usuario, "formación académica", () -> colaboradorPerfilService.agregarEducacion(
                    usuario, f.getInstitucion(), f.getTitulo(), f.getFechaInicio().toString(),
                    f.getFechaFin().toString(), archivosPendientes.abrir(f.getArchivoGuardado(), session), false));
        }

        archivosPendientes.limpiar(session);
    }

    private void intentar(Usuario usuario, String que, Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar {} del pre-registro de {}: {}", que, usuario.getCorreo(), e.getMessage());
        }
    }
}
