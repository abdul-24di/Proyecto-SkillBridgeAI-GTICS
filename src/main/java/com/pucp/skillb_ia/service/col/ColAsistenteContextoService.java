package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ColAsistenteRespuesta.Accion;
import com.pucp.skillb_ia.dto.ColAsistenteRespuesta.CursoTarjeta;
import com.pucp.skillb_ia.dto.ColAsistenteRespuesta.Fuente;
import com.pucp.skillb_ia.dto.ColAsistenteRespuesta.ProyectoTarjeta;
import com.pucp.skillb_ia.dto.ColBonoMensualView;
import com.pucp.skillb_ia.dto.ColProyectoDisponibleView;
import com.pucp.skillb_ia.dto.CursoDisponibleView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.model.enums.TipoPenalizacion;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.PenalizacionService;
import org.jsoup.Jsoup;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.*;


@Service
public class ColAsistenteContextoService {

    private static final int MAX_CURSOS = 10;
    private static final int MAX_PUBLICACIONES = 5;

    // TASK-075: reglas de uso de la plataforma, comprobadas en el código (PenalizacionService,
    // ColaboradorActividadService, ColaboradorProyectoService, ColaboradorCursoService y ColaboradorViewController).
    // Los números salen de las constantes de esos servicios, así la guía no se desactualiza si cambian;
    // ColAsistenteContextoTests lo verifica. Si cambia una regla escrita como texto, actualizar también este texto.
    public static final String GUIA_PLATAFORMA = """

            ## GUÍA DE USO DE SKILLBRIDGE (reglas de la plataforma)
            Páginas del colaborador:
            - Dashboard (/colaborador/dashboard): proyectos, actividades pendientes, horas del mes, strikes, descuento y bono.
            - Perfil (/colaborador/perfil): datos, habilidades con certificado, formación, foto, contraseña y "Mis cursos".
            - Mis proyectos (/colaborador/proyectos): asignaciones, estado e historial; desde el detalle se ven actividades, documentos y el foro del proyecto.
            - Explorar (/colaborador/explorar): proyectos abiertos para postular y catálogo de cursos para solicitar.
            - Foros (/colaborador/foros), Chat con el PM (/colaborador/chat), Mi desempeño (/colaborador/desempeno, con descarga en Excel) y Asistente (/colaborador/asistente).
            Postular a un proyecto (en Explorar): el proyecto debe estar ACTIVO; se elige uno de sus perfiles requeridos; hay que tener esa habilidad VALIDADA por el RM, horas disponibles iguales o mayores a las horas del perfil, cupo libre y ninguna solicitud pendiente o asignación activa en ese proyecto. Al postular se avisa al PM y al RM.
            Actividades: para marcarla lista se sube evidencia PDF, JPG o PNG de %d MB como máximo y un comentario obligatorio de hasta %d caracteres. Solo se puede con la asignación activa. Entregarla después de la fecha límite genera un strike.
            Strikes y descuento: cada actividad entregada tarde (o vencida sin entregar) es un strike; el PM también puede aplicarlo al devolver o confirmar una actividad. Con %d strikes en un mismo proyecto se avisa al PM para que evalúe si el colaborador continúa. El descuento del mes es %d %% por cada bloque completo de %d strikes del mes, sumando todos los proyectos.
            Bono: la meta es %s horas al mes. Cuentan las actividades completadas en el mes y las horas de los cursos cuya evidencia aprobó el RM en el mes. Las horas sobre la meta cuentan para el bono hasta un tope mensual. Si el mes tiene descuento por strikes, no hay bono. Es informativo: la plataforma no registra la nómina.
            Cursos: se solicitan en Explorar y el RM aprueba la inscripción. Todo curso necesita evidencia aprobada por el RM para quedar COMPLETADO. La primera evidencia (PDF, JPG o PNG, %d MB como máximo) se sube antes de la fecha de fin del curso; si vence sin evidencia queda NO_COMPLETADO, sin penalización. Si el RM rechaza la evidencia, se puede reenviar sin plazo. No se puede solicitar un curso si en el mes ya se alcanzó el tope de horas que cuentan para el bono, ni un curso ya terminado o ya completado.
            Perfil: una habilidad nueva necesita certificado y la valida el RM; la formación académica también la aprueba el RM; la experiencia profesional solo la puede editar el Admin.
            """.formatted(
            ColaboradorProyectoService.TAMANO_MAXIMO_EVIDENCIA_BYTES / (1024 * 1024),
            ColaboradorProyectoService.MAX_COMENTARIO_ENTREGA,
            PenalizacionService.MAXIMO_STRIKES,
            PenalizacionService.PORCENTAJE_POR_BLOQUE,
            PenalizacionService.STRIKES_POR_BLOQUE,
            ColaboradorActividadService.META_MENSUAL_HORAS,
            ColaboradorCursoService.TAMANO_MAXIMO_EVIDENCIA_BYTES / (1024 * 1024));

    private static final Set<String> IGNORAR = new HashSet<>(Arrays.asList(
            "como", "para", "que", "con", "los", "las", "una", "uno", "por", "del", "mis", "mas", "muy",
            "puedo", "puede", "quiero", "necesito", "tengo", "sobre", "este", "esta", "esto", "cual",
            "cuales", "hacer", "hay", "donde", "cuando", "desde", "hasta", "pero", "tambien", "proyecto",
            "proyectos", "curso", "cursos", "habilidad", "habilidades", "mejorar", "ayuda", "favor",
            "dime", "explicame", "sus", "son", "ser", "eso", "ese", "algun", "alguna", "debo", "deberia",
            "siguiente", "siguientes", "pasos", "mejor", "tiene", "tienen", "estoy", "quisiera"));

    private static final String[] PALABRAS_DE_CURSO = {
            "curso", "capacit", "aprender", "estudiar", "formacion", "certific", "mejorar",
            "siguiente", "avance", "progreso", "recomiend", "reforzar"};

    public record Contexto(String texto,
                           Map<Long, CursoTarjeta> cursos,
                           Map<Long, Fuente> publicaciones,
                           Map<Long, ProyectoTarjeta> proyectos,
                           Map<Long, Accion> accionesCurso,
                           Map<Long, Accion> accionesProyecto) {}

    private static class Candidato {
        CursoDisponibleView vista;
        int puntaje;
    }

    private static class Resultado {
        PublicacionForo publicacion;
        int puntaje;
    }

    private final UsuarioRepository usuarioRepository;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    private final ExperienciaProfesionalRepository experienciaProfesionalRepository;
    private final EducacionRepository educacionRepository;
    private final CertificadoRepository certificadoRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;
    private final PublicacionForoRepository publicacionForoRepository;
    private final RespuestaForoRepository respuestaForoRepository;
    private final ColaboradorPerfilService colaboradorPerfilService;
    private final ColaboradorCursoService colaboradorCursoService;
    private final ColaboradorProyectoService colaboradorProyectoService;
    private final ColaboradorActividadService colaboradorActividadService;

    public ColAsistenteContextoService(UsuarioRepository usuarioRepository,
                                       ColaboradorHabilidadRepository colaboradorHabilidadRepository,
                                       ExperienciaProfesionalRepository experienciaProfesionalRepository,
                                       EducacionRepository educacionRepository,
                                       CertificadoRepository certificadoRepository,
                                       ColaboradorCursoRepository colaboradorCursoRepository,
                                       AsignacionRepository asignacionRepository,
                                       ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository,
                                       PublicacionForoRepository publicacionForoRepository,
                                       RespuestaForoRepository respuestaForoRepository,
                                       ColaboradorPerfilService colaboradorPerfilService,
                                       ColaboradorCursoService colaboradorCursoService,
                                       ColaboradorProyectoService colaboradorProyectoService,
                                       ColaboradorActividadService colaboradorActividadService) {
        this.usuarioRepository = usuarioRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.experienciaProfesionalRepository = experienciaProfesionalRepository;
        this.educacionRepository = educacionRepository;
        this.certificadoRepository = certificadoRepository;
        this.colaboradorCursoRepository = colaboradorCursoRepository;
        this.asignacionRepository = asignacionRepository;
        this.proyectoHabilidadRequeridaRepository = proyectoHabilidadRequeridaRepository;
        this.publicacionForoRepository = publicacionForoRepository;
        this.respuestaForoRepository = respuestaForoRepository;
        this.colaboradorPerfilService = colaboradorPerfilService;
        this.colaboradorCursoService = colaboradorCursoService;
        this.colaboradorProyectoService = colaboradorProyectoService;
        this.colaboradorActividadService = colaboradorActividadService;
    }


    @Transactional
    public Contexto construir(Long colaboradorId, String mensaje) {
        Usuario col = usuarioRepository.findById(colaboradorId)
                .orElseThrow(() -> new IllegalArgumentException("No encontramos tu usuario."));
        String mensajeN = normalizar(mensaje);
        List<String> palabras = extraerPalabras(mensajeN);

        Map<Long, CursoTarjeta> cursos = new LinkedHashMap<>();
        Map<Long, Fuente> publicaciones = new LinkedHashMap<>();
        Map<Long, ProyectoTarjeta> proyectos = new LinkedHashMap<>();
        Map<Long, Accion> accionesCurso = new LinkedHashMap<>();
        Map<Long, Accion> accionesProyecto = new LinkedHashMap<>();

        StringBuilder t = new StringBuilder();

        List<ColaboradorHabilidad> misHabilidades = colaboradorHabilidadRepository.findByColaboradorAndActivoTrue(col);
        Map<Long, ColaboradorHabilidad> habilidadPorId = new HashMap<>();
        List<String> brechas = new ArrayList<>();
        for (ColaboradorHabilidad ch : misHabilidades) {
            habilidadPorId.put(ch.getHabilidad().getId(), ch);
            boolean basica = ch.getNivelDominio() != null && ch.getNivelDominio().name().equals("BASICO");
            if (basica || ch.getEstadoValidacion() != EstadoValidacion.VALIDADA) {
                brechas.add(normalizar(ch.getHabilidad().getNombre()));
            }
        }

        agregarPerfil(t, col, misHabilidades);
        t.append(GUIA_PLATAFORMA);
        agregarExperienciaYFormacion(t, col);
        agregarCursosDelColaborador(t, col);
        agregarAsignacionesYActividades(t, col);

        List<ColProyectoDisponibleView> vistas = colaboradorProyectoService.listarTodosLosProyectos(col);
        agregarDemandaInterna(t, vistas, habilidadPorId, brechas);
        agregarProyectoConsultado(t, col, mensajeN, vistas, habilidadPorId, brechas, proyectos, accionesProyecto);
        agregarCursosCatalogo(t, col, palabras, mensajeN, brechas, cursos, accionesCurso);
        agregarForos(t, col, palabras, publicaciones);

        return new Contexto(t.toString(), cursos, publicaciones, proyectos, accionesCurso, accionesProyecto);
    }



    private void agregarPerfil(StringBuilder t, Usuario col, List<ColaboradorHabilidad> misHabilidades) {
        t.append("## PERFIL DEL COLABORADOR\n");
        t.append("Nombre: ").append(nz(col.getNombre())).append(' ').append(nz(col.getApellido())).append('\n');
        t.append("Cargo: ").append(col.getCargo() != null ? col.getCargo().getNombre() : "no registrado").append('\n');
        t.append("Nivel de experiencia: ")
                .append(col.getNivelExperiencia() != null ? col.getNivelExperiencia().name() : "no registrado").append('\n');
        t.append("Años de experiencia: ")
                .append(col.getAniosExperiencia() != null ? col.getAniosExperiencia() : "no registrado").append('\n');
        t.append("Horas disponibles por semana: ")
                .append(col.getHorasDisponibles() != null ? col.getHorasDisponibles() : "no registrado").append('\n');
        t.append("Sobre mí: ").append(col.getDescripcion() == null || col.getDescripcion().isBlank()
                ? "no registrado" : limpiar(cortar(col.getDescripcion(), 300))).append('\n');
        t.append("Objetivos profesionales registrados: la plataforma aún no guarda objetivos; usa solo los que el colaborador mencione en esta conversación.\n");
        t.append("Perfil completo al ").append(colaboradorPerfilService.calcularPorcentajeCompletado(col)).append("%.\n");
        List<String> pendientes = colaboradorPerfilService.listarPendientesCompletar(col);
        for (String p : pendientes) {
            t.append("- Pendiente del perfil: ").append(limpiar(p)).append('\n');
        }

        t.append("\n## HABILIDADES DEL COLABORADOR (nivel de dominio | estado de validación del RM)\n");
        if (misHabilidades.isEmpty()) {
            t.append("No tiene habilidades registradas.\n");
        }
        for (ColaboradorHabilidad ch : misHabilidades) {
            t.append("- ").append(limpiar(ch.getHabilidad().getNombre()))
                    .append(" (").append(limpiar(ch.getHabilidad().getCategoria().getNombre())).append(") | ")
                    .append(ch.getNivelDominio()).append(" | ").append(ch.getEstadoValidacion()).append('\n');
        }
    }

    private void agregarExperienciaYFormacion(StringBuilder t, Usuario col) {
        t.append("\n## EXPERIENCIA PROFESIONAL\n");
        List<ExperienciaProfesional> experiencia =
                experienciaProfesionalRepository.findByColaboradorAndActivoTrueOrderByFechaInicioDesc(col);
        if (experiencia.isEmpty()) {
            t.append("No tiene experiencia registrada.\n");
        }
        for (ExperienciaProfesional e : experiencia) {
            t.append("- ").append(limpiar(nz(e.getCargo()))).append(" en ").append(limpiar(nz(e.getEmpresa())))
                    .append(" (").append(e.getFechaInicio() != null ? e.getFechaInicio() : "?").append(" a ")
                    .append(e.isActual() ? "actualidad" : (e.getFechaFin() != null ? e.getFechaFin() : "?"))
                    .append("): ").append(limpiar(cortar(e.getDescripcion(), 160))).append('\n');
        }

        t.append("\n## FORMACIÓN ACADÉMICA Y CERTIFICADOS\n");
        List<Educacion> educacion = educacionRepository.findByColaboradorAndActivoTrue(col);
        for (Educacion e : educacion) {
            t.append("- Formación: ").append(limpiar(e.getTitulo())).append(" - ").append(limpiar(e.getInstitucion()))
                    .append(" | ").append(e.getEstado()).append('\n');
        }
        List<Certificado> certificados = certificadoRepository.findByColaborador(col);
        for (Certificado c : certificados) {
            t.append("- Certificado de ").append(limpiar(c.getHabilidad().getNombre())).append(" | ").append(c.getEstado());
            if (c.getNivelAprobado() != null) {
                t.append(" (nivel aprobado ").append(c.getNivelAprobado()).append(')');
            }
            t.append('\n');
        }
        if (educacion.isEmpty() && certificados.isEmpty()) {
            t.append("No tiene formación ni certificados registrados.\n");
        }
    }

    private void agregarCursosDelColaborador(StringBuilder t, Usuario col) {
        t.append("\n## CURSOS DEL COLABORADOR (solicitudes e inscripciones)\n");
        List<ColaboradorCurso> mios = colaboradorCursoRepository.findByColaborador(col);
        if (mios.isEmpty()) {
            t.append("No tiene cursos solicitados ni inscritos.\n");
        }
        for (ColaboradorCurso cc : mios) {
            t.append("- ").append(limpiar(cc.getCurso().getNombre())).append(" | estado: ").append(cc.getEstado()).append('\n');
        }
    }

    private void agregarAsignacionesYActividades(StringBuilder t, Usuario col) {
        t.append("\n## PROYECTOS DEL COLABORADOR\n");
        List<Asignacion> asignaciones = asignacionRepository.findByColaboradorIdConDetalle(col.getId());
        int mostradas = 0;
        for (Asignacion a : asignaciones) {
            if (a.getEstado() == EstadoAsignacion.RECHAZADA || mostradas >= 15) {
                continue;
            }
            t.append("- ").append(limpiar(a.getProyecto().getNombre())).append(" | asignación: ").append(a.getEstado())
                    .append(" | ").append(a.getHorasSemanales()).append(" h/semana\n");
            mostradas++;
        }
        if (mostradas == 0) {
            t.append("No tiene proyectos asignados ni solicitudes.\n");
        }

        t.append("\n## ACTIVIDADES PENDIENTES\n");
        List<Actividad> pendientes = colaboradorActividadService.listarActividadesPendientes(col);
        int n = 0;
        for (Actividad a : pendientes) {
            if (n >= 10) {
                break;
            }
            t.append("- ").append(limpiar(a.getTitulo())).append(" (").append(limpiar(a.getProyecto().getNombre()))
                    .append(") | límite: ").append(a.getFechaLimite()).append(" | ").append(a.getEstado()).append('\n');
            n++;
        }
        if (n == 0) {
            t.append("No tiene actividades pendientes.\n");
        }
        agregarHorasStrikesYBono(t, col, asignaciones);
    }

    // TASK-075: horas, strikes, descuento y bono del mes. Por decisión del usuario no se envía ningún
    // monto en soles ni dato del sueldo a la IA: el colaborador los ve en su dashboard o en Mi desempeño.
    private void agregarHorasStrikesYBono(StringBuilder t, Usuario col, List<Asignacion> asignaciones) {
        t.append("\n## HORAS, STRIKES Y BONO DEL MES (calculados por el sistema)\n");
        ColBonoMensualView bono = colaboradorActividadService.obtenerResumenBonoMensual(col);
        t.append("Horas trabajadas este mes: ").append(bono.getHorasTrabajadasMes()).append(" de una meta de ")
                .append(bono.getMetaMensual()).append(" h");
        BigDecimal faltan = bono.getMetaMensual().subtract(bono.getHorasTrabajadasMes());
        t.append(faltan.signum() > 0 ? " (faltan " + faltan + " h).\n" : " (meta cumplida).\n");
        t.append("Horas extra sobre la meta: ").append(bono.getHorasExtra())
                .append(" | horas extra que cuentan para el bono: ").append(bono.getHorasExtraBonificables())
                .append(" (tope del mes: ").append(bono.getTopeHorasExtra()).append(" h).\n");

        YearMonth mesActual = YearMonth.now();
        int strikesMes = 0;
        Map<Long, Integer> strikesPorProyecto = new HashMap<>();
        for (Penalizacion p : colaboradorActividadService.listarMisPenalizaciones(col)) {
            if (p.getTipo() != TipoPenalizacion.ACTIVIDAD_TARDIA) {
                continue;
            }
            if (p.getFecha() != null && YearMonth.from(p.getFecha()).equals(mesActual)) {
                strikesMes++;
            }
            if (p.getProyecto() != null) {
                strikesPorProyecto.merge(p.getProyecto().getId(), 1, Integer::sum);
            }
        }
        int porcentajeDescuento = colaboradorActividadService.obtenerPorcentajeDescuentoMesActual(col);
        t.append("Strikes este mes (todos los proyectos): ").append(strikesMes)
                .append(" | descuento del mes: ").append(porcentajeDescuento).append("%.\n");
        for (Asignacion a : asignaciones) {
            if (a.getEstado() == EstadoAsignacion.ACTIVA) {
                t.append("- Strikes en ").append(limpiar(a.getProyecto().getNombre())).append(": ")
                        .append(strikesPorProyecto.getOrDefault(a.getProyecto().getId(), 0)).append(" de ").append(PenalizacionService.MAXIMO_STRIKES).append('\n');
            }
        }

        if (bono.isTuvoDescuentoEsteMes()) {
            t.append("Bono del mes: no aplica, porque este mes tiene descuento por strikes.\n");
        } else if (bono.getHorasExtraBonificables().signum() > 0) {
            t.append("Bono del mes: sí aplica por ").append(bono.getHorasExtraBonificables()).append(" h extra.\n");
        } else {
            t.append("Bono del mes: aún no, porque no tiene horas extra sobre la meta.\n");
        }
        t.append("Montos en soles (bono, descuento, sueldo): no disponibles aquí; el colaborador los ve en su Dashboard o en Mi desempeño (/colaborador/desempeno).\n");
    }



    // Habilidades que piden los proyectos con postulaciones abiertas y que el colaborador aún no tiene validadas.
    private void agregarDemandaInterna(StringBuilder t, List<ColProyectoDisponibleView> vistas,
                                       Map<Long, ColaboradorHabilidad> habilidadPorId, List<String> brechas) {
        Map<String, Integer> demanda = new LinkedHashMap<>();
        for (ColProyectoDisponibleView v : vistas) {
            if (!v.isPostulacionesAbiertas()) {
                continue;
            }
            for (ProyectoHabilidadRequerida r : proyectoHabilidadRequeridaRepository.findByProyecto(v.getProyecto())) {
                ColaboradorHabilidad mia = habilidadPorId.get(r.getHabilidad().getId());
                boolean la_tiene = mia != null && mia.getEstadoValidacion() == EstadoValidacion.VALIDADA;
                if (!la_tiene) {
                    demanda.merge(r.getHabilidad().getNombre(), 1, Integer::sum);
                }
            }
        }
        List<Map.Entry<String, Integer>> orden = new ArrayList<>(demanda.entrySet());
        Collections.sort(orden, (a, b) -> b.getValue() - a.getValue());

        t.append("\n## HABILIDADES MÁS PEDIDAS EN PROYECTOS ABIERTOS QUE EL COLABORADOR AÚN NO TIENE VALIDADAS\n");
        if (orden.isEmpty()) {
            t.append("Ninguna: no hay proyectos abiertos con requisitos que le falten.\n");
        }
        int limite = Math.min(orden.size(), 5);
        for (int i = 0; i < limite; i++) {
            t.append("- ").append(limpiar(orden.get(i).getKey())).append(" (pedida en ")
                    .append(orden.get(i).getValue()).append(" proyecto(s) abierto(s))\n");
            brechas.add(normalizar(orden.get(i).getKey()));
        }
    }

    // Si el mensaje nombra un proyecto visible para el colaborador, el SISTEMA calcula la brecha.
    private void agregarProyectoConsultado(StringBuilder t, Usuario col, String mensajeN,
                                           List<ColProyectoDisponibleView> vistas,
                                           Map<Long, ColaboradorHabilidad> habilidadPorId, List<String> brechas,
                                           Map<Long, ProyectoTarjeta> proyectos, Map<Long, Accion> accionesProyecto) {
        ColProyectoDisponibleView elegido = null;
        for (ColProyectoDisponibleView v : vistas) {
            String nombre = normalizar(v.getProyecto().getNombre());
            if (nombre.isBlank() || !mensajeN.contains(nombre)) {
                continue;
            }
            if (elegido == null || nombre.length() > normalizar(elegido.getProyecto().getNombre()).length()) {
                elegido = v;
            }
        }
        t.append("\n## PROYECTOS VISIBLES PARA EL COLABORADOR (nombres)\n");
        int n = 0;
        for (ColProyectoDisponibleView v : vistas) {
            if (n >= 15) {
                break;
            }
            t.append("- ").append(limpiar(v.getProyecto().getNombre())).append(" | ").append(v.getProyecto().getEstado()).append('\n');
            n++;
        }
        if (elegido == null) {
            t.append("(El colaborador no nombró un proyecto concreto. Si pregunta qué le falta para un proyecto, pídele que indique cuál.)\n");
            return;
        }

        Proyecto p = elegido.getProyecto();
        proyectos.put(p.getId(), new ProyectoTarjeta(p.getId(), p.getNombre(), p.getEstado().name(), "/colaborador/explorar"));
        t.append("\n## ANÁLISIS DE BRECHA PARA EL PROYECTO \"").append(limpiar(p.getNombre()))
                .append("\" [[PROY:").append(p.getId()).append("]] (calculado por el sistema, no lo recalcules)\n");
        t.append("Estado del proyecto: ").append(p.getEstado()).append(". Postulaciones abiertas: ")
                .append(elegido.isPostulacionesAbiertas() ? "sí" : "no").append(". Cupos: ")
                .append(elegido.getCuposDisponibles()).append(". El colaborador ya tiene solicitud o asignación: ")
                .append(elegido.isYaTieneSolicitud() ? "sí" : "no").append(".\n");
        t.append("Descripción: ").append(limpiar(cortar(p.getDescripcion(), 300))).append('\n');

        Accion propuesta = null;
        List<ProyectoHabilidadRequerida> requisitos = proyectoHabilidadRequeridaRepository.findByProyecto(p);
        if (requisitos.isEmpty()) {
            t.append("El proyecto no tiene habilidades requeridas registradas.\n");
        }
        for (ProyectoHabilidadRequerida r : requisitos) {
            Habilidad h = r.getHabilidad();
            ColaboradorHabilidad mia = habilidadPorId.get(h.getId());
            String situacion;
            boolean cumple = false;
            if (mia == null) {
                situacion = "NO_LA_TIENE en su perfil";
            } else if (mia.getEstadoValidacion() == EstadoValidacion.PENDIENTE) {
                situacion = "LA_TIENE pero está PENDIENTE de validación del RM";
            } else if (mia.getEstadoValidacion() == EstadoValidacion.RECHAZADA) {
                situacion = "su habilidad fue RECHAZADA por el RM";
            } else if (r.getNivelRequerido() != null && mia.getNivelDominio().compareTo(r.getNivelRequerido()) < 0) {
                situacion = "NIVEL_INSUFICIENTE (tiene " + mia.getNivelDominio() + ")";
            } else {
                situacion = "CUMPLE";
                cumple = true;
            }
            if (!cumple) {
                brechas.add(normalizar(h.getNombre()));
            }
            BigDecimal horasPerfil = r.getHorasSemanales() != null ? r.getHorasSemanales() : p.getHorasSemanalesRequeridas();
            boolean horasOk = col.getHorasDisponibles() != null && horasPerfil != null
                    && col.getHorasDisponibles().compareTo(horasPerfil) >= 0;
            t.append("- Perfil ").append(limpiar(h.getNombre())).append(" | nivel requerido: ")
                    .append(r.getNivelRequerido() != null ? r.getNivelRequerido() : "sin mínimo")
                    .append(" | ").append(horasPerfil).append(" h/semana | situación del colaborador: ").append(situacion)
                    .append(" | horas disponibles suficientes: ").append(horasOk ? "sí" : "no").append('\n');
            if (cumple && horasOk && propuesta == null) {
                propuesta = new Accion("SOLICITAR_PROYECTO", p.getId(), h.getId(),
                        "Solicitar incorporación a " + p.getNombre() + " (perfil " + h.getNombre() + ")");
            }
        }

        if (elegido.isPostulacionesAbiertas() && !elegido.isYaTieneSolicitud() && propuesta != null) {
            accionesProyecto.put(p.getId(), propuesta);
            t.append("ACCIÓN DISPONIBLE: el colaborador puede postular a este proyecto. Si lo recomiendas, añade [[ACCION:SOLICITAR_PROYECTO:")
                    .append(p.getId()).append("]]\n");
        } else {
            t.append("ACCIÓN NO DISPONIBLE: no ofrezcas postular desde el asistente (postulaciones cerradas, ya tiene solicitud, o aún no cumple un perfil con sus horas).\n");
        }
    }



    private void agregarCursosCatalogo(StringBuilder t, Usuario col, List<String> palabras, String mensajeN,
                                       List<String> brechas, Map<Long, CursoTarjeta> cursos,
                                       Map<Long, Accion> accionesCurso) {
        List<CursoDisponibleView> catalogo = colaboradorCursoService.listarCursosDisponibles(col);
        boolean pideCursos = false;
        for (String clave : PALABRAS_DE_CURSO) {
            if (mensajeN.contains(clave)) {
                pideCursos = true;
                break;
            }
        }
        List<Candidato> candidatos = new ArrayList<>();
        for (CursoDisponibleView vista : catalogo) {
            Curso c = vista.getCurso();
            String texto = normalizar(nz(c.getNombre()) + " " + nz(c.getDescripcion()) + " " + nz(c.getCategoria()));
            int puntaje = 0;
            for (String palabra : palabras) {
                if (texto.contains(palabra)) {
                    puntaje += 2;
                }
            }
            for (String brecha : brechas) {
                if (!brecha.isBlank() && texto.contains(brecha)) {
                    puntaje += 1;
                }
            }
            Candidato cand = new Candidato();
            cand.vista = vista;
            cand.puntaje = puntaje;
            candidatos.add(cand);
        }
        Collections.sort(candidatos, (a, b) -> b.puntaje - a.puntaje);

        t.append("\n## CURSOS DEL CATÁLOGO REAL DE SKILLBRIDGE (únicos cursos que puedes recomendar)\n");
        int elegidos = 0;
        for (Candidato cand : candidatos) {
            if (elegidos >= MAX_CURSOS) {
                break;
            }
            if (cand.puntaje == 0 && !pideCursos) {
                break;
            }
            CursoDisponibleView vista = cand.vista;
            Curso c = vista.getCurso();
            String inicio = c.getFechaInicio() != null ? c.getFechaInicio().toString() : null;
            String fin = c.getFechaFin() != null ? c.getFechaFin().toString() : null;
            String modalidad = c.getModalidad() != null ? c.getModalidad().name() : null;
            String estado = vista.getEstadoTexto() != null ? vista.getEstadoTexto() : "Sin solicitud";
            cursos.put(c.getId(), new CursoTarjeta(c.getId(), c.getNombre(), c.getCategoria(), modalidad, inicio, fin,
                    c.getHoras() != null ? c.getHoras().toPlainString() : null,
                    cortar(c.getDescripcion(), 240), estado, vista.isPuedeSolicitar()));
            t.append("- [[CURSO:").append(c.getId()).append("]] \"").append(limpiar(c.getNombre())).append("\"")
                    .append(" | categoría: ").append(limpiar(nz(c.getCategoria())))
                    .append(" | modalidad: ").append(modalidad != null ? modalidad : "no registrada")
                    .append(" | inicio: ").append(inicio != null ? inicio : "no registrada")
                    .append(" | fin: ").append(fin != null ? fin : "no registrada")
                    .append(c.isAutodidacta() ? " | autodidacta" : "")
                    .append(" | horas: ").append(c.getHoras())
                    .append(" | estado del colaborador: ").append(estado)
                    .append(" | puede solicitarlo: ").append(vista.isPuedeSolicitar() ? "sí" : "no")
                    .append(" | descripción: ").append(limpiar(cortar(c.getDescripcion(), 200))).append('\n');
            if (vista.isPuedeSolicitar()) {
                accionesCurso.put(c.getId(), new Accion("SOLICITAR_CURSO", c.getId(), null,
                        "Solicitar inscripción a " + c.getNombre()));
                t.append("  ACCIÓN DISPONIBLE: [[ACCION:SOLICITAR_CURSO:").append(c.getId()).append("]]\n");
            }
            elegidos++;
        }
        if (elegidos == 0) {
            t.append("No hay cursos del catálogo relacionados con esta consulta.\n");
        }
    }



    private boolean puedeLeer(Usuario col, Foro foro) {
        if (foro.isEsPublico()) {
            return true;
        }
        return foro.getProyecto() != null && asignacionRepository.tieneAccesoVigente(foro.getProyecto(), col);
    }

    private void agregarForos(StringBuilder t, Usuario col, List<String> palabras, Map<Long, Fuente> publicaciones) {
        t.append("\n## BÚSQUEDA EN LOS FOROS DE SKILLBRIDGE (solo foros a los que el colaborador tiene acceso)\n");
        if (palabras.isEmpty()) {
            t.append("Sin resultados: la consulta no tiene términos de búsqueda.\n");
            return;
        }
        int limite = Math.min(palabras.size(), 5);
        Map<Long, PublicacionForo> candidatas = new LinkedHashMap<>();
        Map<Long, RespuestaForo> respuestaPorPublicacion = new HashMap<>();

        for (int i = 0; i < limite; i++) {
            String clave = palabras.get(i);
            for (PublicacionForo p : publicacionForoRepository.buscarPorPalabra(clave, PageRequest.of(0, 30))) {
                if (puedeLeer(col, p.getForo())) {
                    candidatas.putIfAbsent(p.getId(), p);
                }
            }
            for (RespuestaForo r : respuestaForoRepository.buscarPorPalabra(clave, PageRequest.of(0, 30))) {
                PublicacionForo p = r.getPublicacion();
                if (!puedeLeer(col, p.getForo())) {
                    continue;
                }
                candidatas.putIfAbsent(p.getId(), p);
                RespuestaForo previa = respuestaPorPublicacion.get(p.getId());
                if (previa == null || (r.isEsSolucion() && !previa.isEsSolucion())) {
                    respuestaPorPublicacion.put(p.getId(), r);
                }
            }
        }

        List<Resultado> resultados = new ArrayList<>();
        for (PublicacionForo p : candidatas.values()) {
            String titulo = normalizar(p.getTitulo());
            String texto = normalizar(textoPlano(p.getContenido(), 3000));
            int puntaje = 0;
            for (int i = 0; i < limite; i++) {
                if (titulo.contains(palabras.get(i))) {
                    puntaje += 3;
                }
                if (texto.contains(palabras.get(i))) {
                    puntaje += 1;
                }
            }
            if (respuestaPorPublicacion.containsKey(p.getId())) {
                puntaje += 2;
            }
            if (puntaje > 0) {
                Resultado res = new Resultado();
                res.publicacion = p;
                res.puntaje = puntaje;
                resultados.add(res);
            }
        }
        Collections.sort(resultados, (a, b) -> b.puntaje - a.puntaje);

        if (resultados.isEmpty()) {
            t.append("Sin resultados relevantes en los foros para esta consulta.\n");
            return;
        }
        int mostrados = Math.min(resultados.size(), MAX_PUBLICACIONES);
        for (int i = 0; i < mostrados; i++) {
            PublicacionForo p = resultados.get(i).publicacion;
            Foro foro = p.getForo();
            RespuestaForo solucion = respuestaForoRepository.findByPublicacionAndEsSolucionTrue(p).orElse(null);
            if (solucion != null && !solucion.isActivo()) {
                solucion = null;
            }
            RespuestaForo respuesta = solucion != null ? solucion : respuestaPorPublicacion.get(p.getId());

            String url = "/colaborador/foros/detalle?foroId=" + foro.getId() + "#pub-" + p.getId();
            publicaciones.put(p.getId(), new Fuente(p.getId(), p.getTitulo(), foro.getNombre(), url, solucion != null));

            t.append("- [[PUB:").append(p.getId()).append("]] título: \"").append(limpiar(p.getTitulo()))
                    .append("\" | foro: ").append(limpiar(foro.getNombre()))
                    .append(" | resuelta: ").append(solucion != null ? "sí" : "no").append('\n');
            t.append("  <extracto_foro>").append(limpiar(cortar(textoPlano(p.getContenido(), 600), 350)))
                    .append("</extracto_foro>\n");
            if (respuesta != null) {
                t.append("  <extracto_foro>Respuesta").append(respuesta.isEsSolucion() ? " (marcada como solución)" : "")
                        .append(": ").append(limpiar(cortar(textoPlano(respuesta.getContenido(), 600), 300)))
                        .append("</extracto_foro>\n");
            }
        }
    }



    public static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim();
    }

    private List<String> extraerPalabras(String mensajeN) {
        String[] partes = mensajeN.split("[^a-z0-9+#.]+");
        List<String> palabras = new ArrayList<>();
        for (String p : partes) {
            String limpia = p.replaceAll("^[.]+|[.]+$", "");
            if (limpia.length() >= 3 && !IGNORAR.contains(limpia) && !palabras.contains(limpia)) {
                palabras.add(limpia);
            }
        }

        Collections.sort(palabras, (a, b) -> b.length() - a.length());
        return palabras;
    }

    private String textoPlano(String html, int maximo) {
        if (html == null) {
            return "";
        }
        String texto = Jsoup.parse(html).text();
        return texto.length() > maximo ? texto.substring(0, maximo) : texto;
    }

    private String cortar(String s, int maximo) {
        if (s == null) {
            return "";
        }
        return s.length() > maximo ? s.substring(0, maximo) + "..." : s;
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }


    private String limpiar(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("[[", "(").replace("]]", ")").replace("<extracto_foro>", "").replace("</extracto_foro>", "");
    }
}