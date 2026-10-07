package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.auth.ActivarCuentaForm;
import com.pucp.skillb_ia.dto.auth.CertificadoPreRegistroForm;
import com.pucp.skillb_ia.dto.auth.EstudioPreRegistroForm;
import com.pucp.skillb_ia.dto.auth.ExperienciaForm;
import com.pucp.skillb_ia.model.ExperienciaProfesional;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Pre-registro del colaborador (se completa al activar la cuenta): valida y guarda su CV, experiencia
// profesional, certificados de habilidades y formación académica. Reutiliza los métodos que ya usa
// su perfil, así que lo cargado aparece solo en "Mi perfil" y el Admin/RM no tienen que digitarlo.
@Service
public class PreRegistroColaboradorService {

    private static final Logger log = LoggerFactory.getLogger(PreRegistroColaboradorService.class);
    private static final int MAX_FILAS = 10;

    private final ColaboradorPerfilService colaboradorPerfilService;

    public PreRegistroColaboradorService(ColaboradorPerfilService colaboradorPerfilService) {
        this.colaboradorPerfilService = colaboradorPerfilService;
    }

    // ============================================================
    // VALIDACIÓN (antes de crear la cuenta)
    // ============================================================

    // Solo se validan las filas que el usuario empezó a llenar; una fila en blanco se ignora.
    public void validar(ActivarCuentaForm form, BindingResult result) {
        // El teléfono (9 dígitos, igual que el perfil del colaborador) ya lo validó el formulario.
        try {
            colaboradorPerfilService.validarCv(form.getCv());
        } catch (IllegalArgumentException e) {
            result.rejectValue("cv", "cv.invalido", e.getMessage());
        }
        validarExperiencias(form.getExperiencias(), result);
        validarCertificados(form.getCertificados(), result);
        validarEstudios(form.getEstudios(), result);
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
                result.rejectValue(campo + "fechaInicio", "exp.inicio", "La fecha de inicio es obligatoria.");
            } else if (f.getFechaInicio().isAfter(LocalDate.now())) {
                result.rejectValue(campo + "fechaInicio", "exp.inicio", "La fecha de inicio no puede ser futura.");
            }
            if (!f.isActual()) {
                if (f.getFechaFin() == null) {
                    result.rejectValue(campo + "fechaFin", "exp.fin", "Indica la fecha de fin o marca que trabajas ahí actualmente.");
                } else if (f.getFechaInicio() != null && f.getFechaFin().isBefore(f.getFechaInicio())) {
                    result.rejectValue(campo + "fechaFin", "exp.fin", "La fecha de fin no puede ser anterior a la de inicio.");
                }
            }
        }
    }

    private void validarCertificados(List<CertificadoPreRegistroForm> filas, BindingResult result) {
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
                result.rejectValue(campo + "habilidadId", "cert.habilidad", "Selecciona la habilidad.");
            } else if (!catalogo.contains(f.getHabilidadId())) {
                result.rejectValue(campo + "habilidadId", "cert.habilidad", "La habilidad seleccionada no existe.");
            } else if (!yaElegidas.add(f.getHabilidadId())) {
                result.rejectValue(campo + "habilidadId", "cert.habilidad", "Ya agregaste un certificado para esta habilidad.");
            }
            if (f.getNivel() == null) {
                result.rejectValue(campo + "nivel", "cert.nivel", "Selecciona tu nivel.");
            }
            try {
                colaboradorPerfilService.validarArchivoCertificado(f.getArchivo());
            } catch (IllegalArgumentException e) {
                result.rejectValue(campo + "archivo", "cert.archivo", e.getMessage());
            }
        }
    }

    private void validarEstudios(List<EstudioPreRegistroForm> filas, BindingResult result) {
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
                result.rejectValue(campo + "fechaInicio", "est.inicio", "Indica la fecha de inicio.");
            }
            if (f.getFechaFin() == null) {
                result.rejectValue(campo + "fechaFin", "est.fin", "Indica la fecha de fin.");
            } else if (f.getFechaInicio() != null && f.getFechaFin().isBefore(f.getFechaInicio())) {
                result.rejectValue(campo + "fechaFin", "est.fin", "La fecha de fin no puede ser anterior a la de inicio.");
            } else if (f.getFechaFin().isAfter(LocalDate.now())) {
                result.rejectValue(campo + "fechaFin", "est.fin", "La fecha de fin no puede ser futura.");
            }
            try {
                colaboradorPerfilService.validarArchivoCertificado(f.getArchivo());
            } catch (IllegalArgumentException e) {
                result.rejectValue(campo + "archivo", "est.archivo", e.getMessage());
            }
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
    // GUARDADO (la cuenta ya está creada)
    // ============================================================

    // Cada fila se guarda por separado: si una falla (p. ej. el almacenamiento), el resto sigue y el
    // colaborador puede completarla después desde su perfil.
    public void guardar(Usuario usuario, ActivarCuentaForm form) {
        intentar(usuario, "CV", () -> colaboradorPerfilService.actualizarCv(usuario, form.getCv()));

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
                    usuario, f.getHabilidadId(), null, null, f.getNivel(), f.getArchivo()));
        }

        for (EstudioPreRegistroForm f : form.getEstudios()) {
            if (f.estaVacia()) continue;
            intentar(usuario, "formación académica", () -> colaboradorPerfilService.agregarEducacion(
                    usuario, f.getInstitucion(), f.getTitulo(), f.getFechaInicio().toString(),
                    f.getFechaFin().toString(), f.getArchivo(), false));
        }
    }

    private void intentar(Usuario usuario, String que, Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar {} del pre-registro de {}: {}", que, usuario.getCorreo(), e.getMessage());
        }
    }
}
