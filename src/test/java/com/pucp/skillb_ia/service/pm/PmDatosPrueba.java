package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequerida;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.model.enums.TipoForo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Datos en memoria para las pruebas unitarias del Project Manager (sin Spring ni H2).
 * Cada método arma una entidad mínima con su identificador; cada prueba ajusta los campos que necesite.
 */
public final class PmDatosPrueba {

    public static final String ROL_PM = "PROJECT_MANAGER";
    public static final String ROL_RM = "RESOURCE_MANAGER";
    public static final String ROL_COLABORADOR = "COLABORADOR";
    public static final String ROL_ADMIN = "ADMINISTRADOR";

    private PmDatosPrueba() {
    }

    public static Rol rol(String nombre) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        return rol;
    }

    public static Usuario usuario(Long id, String rol) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNombre("Usuario" + id);
        u.setApellido("Prueba");
        u.setCorreo("usuario" + id + "@prueba.local");
        u.setRol(rol(rol));
        u.setActivo(true);
        u.setPasswordHash("hash-actual");
        return u;
    }

    public static Usuario pm(Long id) {
        return usuario(id, ROL_PM);
    }

    public static Usuario rm(Long id) {
        return usuario(id, ROL_RM);
    }

    public static Usuario admin(Long id) {
        return usuario(id, ROL_ADMIN);
    }

    public static Usuario colaborador(Long id) {
        Usuario u = usuario(id, ROL_COLABORADOR);
        u.setHorasDisponibles(new BigDecimal("40"));
        return u;
    }

    public static Proyecto proyecto(Long id, Usuario pm, EstadoProyecto estado) {
        Proyecto p = new Proyecto();
        p.setId(id);
        p.setNombre("Proyecto " + id);
        p.setDescripcion("Descripción del proyecto " + id);
        p.setPm(pm);
        p.setEstado(estado);
        p.setPrioridad(Prioridad.MEDIA);
        p.setFechaInicio(LocalDate.of(2026, 1, 5));
        p.setFechaFinEstimada(LocalDate.of(2026, 6, 30));
        p.setPresupuesto(new BigDecimal("50000"));
        p.setPresupuestoSolicitado(new BigDecimal("50000"));
        p.setColaboradoresRequeridos(3);
        p.setHorasSemanalesRequeridas(new BigDecimal("20"));
        return p;
    }

    public static Proyecto proyecto(Long id, Usuario pm) {
        return proyecto(id, pm, EstadoProyecto.ACTIVO);
    }

    public static Asignacion asignacion(Long id, Proyecto proyecto, Usuario colaborador,
                                        String horasSemanales, EstadoAsignacion estado) {
        Asignacion a = new Asignacion();
        a.setId(id);
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setHorasSemanales(new BigDecimal(horasSemanales));
        a.setEstado(estado);
        a.setOrigen(OrigenAsignacion.PROPUESTA_RM);
        return a;
    }

    public static Actividad actividad(Long id, Proyecto proyecto, Usuario colaborador,
                                      EstadoActividad estado, LocalDate fechaLimite) {
        Actividad act = new Actividad();
        act.setId(id);
        act.setProyecto(proyecto);
        act.setColaborador(colaborador);
        act.setTitulo("Actividad " + id);
        act.setDescripcion("Detalle " + id);
        act.setHorasEstimadas(new BigDecimal("8"));
        act.setFechaLimite(fechaLimite);
        act.setEstado(estado);
        act.setActivo(true);
        return act;
    }

    public static Habilidad habilidad(Long id, String nombre) {
        Habilidad h = new Habilidad();
        h.setId(id);
        h.setNombre(nombre);
        return h;
    }

    public static ProyectoHabilidadRequerida requisito(Proyecto proyecto, Habilidad habilidad,
                                                       NivelDominio nivel, int cantidad) {
        ProyectoHabilidadRequerida phr = new ProyectoHabilidadRequerida();
        phr.setProyecto(proyecto);
        phr.setHabilidad(habilidad);
        phr.setNivelRequerido(nivel);
        phr.setCantidadPersonas(cantidad);
        phr.setHorasSemanales(new BigDecimal("10"));
        return phr;
    }

    public static Foro foroProyecto(Long id, Proyecto proyecto, boolean esPublico) {
        Foro f = new Foro();
        f.setId(id);
        f.setNombre("Foro — " + proyecto.getNombre());
        f.setProyecto(proyecto);
        f.setTipo(TipoForo.PROYECTO);
        f.setEsPublico(esPublico);
        f.setFechaCreacion(LocalDateTime.of(2026, 1, 10, 9, 0));
        return f;
    }

    public static Foro foroGeneral(Long id, boolean esPublico) {
        Foro f = new Foro();
        f.setId(id);
        f.setNombre(esPublico ? "Comunidad" : "Anuncios");
        f.setProyecto(null);
        f.setTipo(TipoForo.GENERAL);
        f.setEsPublico(esPublico);
        f.setFechaCreacion(LocalDateTime.of(2026, 1, 10, 9, 0));
        return f;
    }

    public static PublicacionForo publicacion(Long id, Foro foro, Usuario autor, String titulo) {
        PublicacionForo pub = new PublicacionForo();
        pub.setId(id);
        pub.setForo(foro);
        pub.setAutor(autor);
        pub.setTitulo(titulo);
        pub.setContenido("<p>Contenido de prueba</p>");
        pub.setActivo(true);
        pub.setFechaCreacion(LocalDateTime.of(2026, 2, 1, 10, 0));
        return pub;
    }

    public static RespuestaForo respuesta(Long id, PublicacionForo publicacion, Usuario autor) {
        RespuestaForo r = new RespuestaForo();
        r.setId(id);
        r.setPublicacion(publicacion);
        r.setAutor(autor);
        r.setContenido("<p>Respuesta de prueba</p>");
        r.setActivo(true);
        return r;
    }
}
