package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Certificado;
import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import com.pucp.skillb_ia.model.Educacion;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.EstadoSolicitudPersonal;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.security.UsuarioDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Datos en memoria para las pruebas unitarias del RM (sin Spring ni H2).
// Cada método arma una entidad con su id y lo mínimo para que el servicio funcione;
// la prueba ajusta con setters lo que necesite. No se guarda nada en ninguna base.
public final class RmDatosPrueba {

    public static final String ROL_RM = "RESOURCE_MANAGER";
    public static final String ROL_PM = "PROJECT_MANAGER";
    public static final String ROL_COLABORADOR = "COLABORADOR";

    private RmDatosPrueba() {
    }

    // ---------- Usuarios ----------

    public static Rol rol(String nombre) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        return rol;
    }

    public static Usuario usuario(Long id, String rol) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Usuario" + id);
        usuario.setApellido("Prueba");
        usuario.setCorreo("usuario" + id + "@prueba.local");
        usuario.setRol(rol(rol));
        usuario.setActivo(true);
        return usuario;
    }

    public static Usuario rm(Long id) {
        return usuario(id, ROL_RM);
    }

    public static Usuario pm(Long id) {
        return usuario(id, ROL_PM);
    }

    public static Usuario colaborador(Long id) {
        return colaborador(id, "3200");
    }

    // sueldo base mensual en soles; null deja al colaborador sin sueldo.
    public static Usuario colaborador(Long id, String sueldoBase) {
        Usuario usuario = usuario(id, ROL_COLABORADOR);
        usuario.setSueldoBase(sueldoBase == null ? null : new BigDecimal(sueldoBase));
        usuario.setHorasDisponibles(new BigDecimal("40"));
        return usuario;
    }

    // ---------- Proyectos y asignaciones ----------

    // Proyecto con fechas fijas y presupuesto; null en cualquiera lo deja sin ese dato.
    public static Proyecto proyecto(Long id, LocalDate inicio, LocalDate fin, String presupuesto) {
        Proyecto proyecto = new Proyecto();
        proyecto.setId(id);
        proyecto.setNombre("Proyecto" + id);
        proyecto.setFechaInicio(inicio);
        proyecto.setFechaFinEstimada(fin);
        proyecto.setPresupuesto(presupuesto == null ? null : new BigDecimal(presupuesto));
        return proyecto;
    }

    // Proyecto de 70 días (10 semanas exactas) con S/ 100 000 de presupuesto.
    public static Proyecto proyecto(Long id) {
        LocalDate inicio = LocalDate.of(2026, 1, 5);
        return proyecto(id, inicio, inicio.plusDays(70), "100000");
    }

    public static Asignacion asignacion(Long id, Proyecto proyecto, Usuario colaborador, String horasSemanales,
                                        EstadoAsignacion estado) {
        Asignacion asignacion = new Asignacion();
        asignacion.setId(id);
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(colaborador);
        asignacion.setHorasSemanales(new BigDecimal(horasSemanales));
        asignacion.setEstado(estado);
        return asignacion;
    }

    // ---------- Solicitudes de personal ----------

    // Solicitud del PM del proyecto por 2 colaboradores, en el estado indicado.
    public static SolicitudPersonal solicitudPersonal(Long id, Proyecto proyecto, EstadoSolicitudPersonal estado) {
        SolicitudPersonal solicitud = new SolicitudPersonal();
        solicitud.setId(id);
        solicitud.setProyecto(proyecto);
        solicitud.setCantidadColaboradores(2);
        solicitud.setEstado(estado);
        return solicitud;
    }

    // ---------- Formación académica ----------

    // Formación activa y PENDIENTE del colaborador, con documento adjunto y asignada a "rmResponsable".
    public static Educacion educacion(Long id, Usuario colaborador, Usuario rmResponsable) {
        Educacion educacion = new Educacion();
        educacion.setId(id);
        educacion.setColaborador(colaborador);
        educacion.setRmResponsable(rmResponsable);
        educacion.setTitulo("Titulo" + id);
        educacion.setInstitucion("Institucion" + id);
        educacion.setArchivoUrl("/uploads/educacion/" + id + ".pdf");
        educacion.setEstado(EstadoCertificado.PENDIENTE);
        educacion.setActivo(true);
        return educacion;
    }

    // ---------- Certificados y habilidades ----------

    public static Habilidad habilidad(Long id, String nombre) {
        Habilidad habilidad = new Habilidad();
        habilidad.setId(id);
        habilidad.setNombre(nombre);
        return habilidad;
    }

    // Certificado PENDIENTE del colaborador para "habilidad", asignado a "rmResponsable" y subido hoy.
    public static Certificado certificado(Long id, Usuario colaborador, Habilidad habilidad, Usuario rmResponsable) {
        Certificado certificado = new Certificado();
        certificado.setId(id);
        certificado.setColaborador(colaborador);
        certificado.setHabilidad(habilidad);
        certificado.setRmResponsable(rmResponsable);
        certificado.setArchivoUrl("/uploads/certificados/cert" + id + ".pdf");
        certificado.setEstado(EstadoCertificado.PENDIENTE);
        certificado.setFechaSubida(LocalDateTime.now());
        return certificado;
    }

    // Habilidad activa del perfil del colaborador, PENDIENTE de validación.
    public static ColaboradorHabilidad colaboradorHabilidad(Usuario colaborador, Habilidad habilidad, NivelDominio nivel) {
        ColaboradorHabilidad item = new ColaboradorHabilidad();
        item.setColaborador(colaborador);
        item.setHabilidad(habilidad);
        item.setNivelDominio(nivel);
        item.setActivo(true);
        return item;
    }

    // ---------- Cursos ----------

    // Curso activo de 20 h, categoría "Datos", sin fechas (no caduca).
    public static Curso curso(Long id) {
        Curso curso = new Curso();
        curso.setId(id);
        curso.setNombre("Curso" + id);
        curso.setCategoria("Datos");
        curso.setHoras(new BigDecimal("20"));
        curso.setActivo(true);
        return curso;
    }

    // Inscripción del colaborador al curso, asignada a "rmResponsable".
    public static ColaboradorCurso inscripcion(Long id, Usuario colaborador, Curso curso, Usuario rmResponsable,
                                               OrigenCurso origen, EstadoColaboradorCurso estado) {
        ColaboradorCurso inscripcion = new ColaboradorCurso();
        inscripcion.setId(id);
        inscripcion.setColaborador(colaborador);
        inscripcion.setCurso(curso);
        inscripcion.setRmResponsable(rmResponsable);
        inscripcion.setOrigen(origen);
        inscripcion.setEstado(estado);
        inscripcion.setFechaSolicitud(LocalDateTime.of(2026, 1, 10, 9, 0).plusDays(id));
        return inscripcion;
    }

    // ---------- Foros ----------

    // Foro público creado el 2026-01-01: de PROYECTO si "proyecto" no es null; si no, el GENERAL del Admin.
    public static com.pucp.skillb_ia.model.Foro foro(Long id, Proyecto proyecto) {
        com.pucp.skillb_ia.model.Foro foro = new com.pucp.skillb_ia.model.Foro();
        foro.setId(id);
        foro.setNombre("Foro" + id);
        foro.setProyecto(proyecto);
        foro.setTipo(proyecto == null ? com.pucp.skillb_ia.model.enums.TipoForo.GENERAL
                : com.pucp.skillb_ia.model.enums.TipoForo.PROYECTO);
        foro.setEsPublico(true);
        foro.setFechaCreacion(LocalDateTime.of(2026, 1, 1, 9, 0));
        return foro;
    }

    // ---------- Talent Matching ----------

    // Requisito del proyecto: "cantidad" personas con "habilidad" al nivel indicado (null = cualquier nivel).
    public static com.pucp.skillb_ia.model.ProyectoHabilidadRequerida requisito(Proyecto proyecto, Habilidad habilidad,
                                                                               NivelDominio nivel, int cantidad) {
        com.pucp.skillb_ia.model.ProyectoHabilidadRequerida requisito = new com.pucp.skillb_ia.model.ProyectoHabilidadRequerida();
        requisito.setProyecto(proyecto);
        requisito.setHabilidad(habilidad);
        requisito.setNivelRequerido(nivel);
        requisito.setCantidadPersonas(cantidad);
        return requisito;
    }

    // Vista del proyecto tal como la arma RmProyectoConsultaService, sin equipo ni presupuesto calculado.
    public static com.pucp.skillb_ia.dto.RmProyectoView proyectoView(Proyecto proyecto, int vacantes) {
        return new com.pucp.skillb_ia.dto.RmProyectoView(proyecto, "PM Prueba",
                String.valueOf(proyecto.getEstado()), "Media", 1, vacantes, 0,
                java.util.List.of(), java.util.List.of(), null);
    }

    // ---------- Sesión (para servicios que usan RmAlcanceService.idRmActual) ----------

    // Deja a "usuario" como el usuario autenticado. Llamar a limpiarSesion() en @AfterEach.
    public static void sesionComo(Usuario usuario) {
        UsuarioDetails details = new UsuarioDetails(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    public static void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }
}
