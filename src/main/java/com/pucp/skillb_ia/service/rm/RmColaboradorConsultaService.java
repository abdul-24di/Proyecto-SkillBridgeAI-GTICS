package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmColaboradorDetalle;
import com.pucp.skillb_ia.dto.RmColaboradorResumen;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.Educacion;
import com.pucp.skillb_ia.model.ExperienciaProfesional;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.CertificadoRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.ConfiguracionSistemaRepository;
import com.pucp.skillb_ia.repository.EducacionRepository;
import com.pucp.skillb_ia.repository.ExperienciaProfesionalRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@Service
public class RmColaboradorConsultaService {

    private static final String ROL_COLABORADOR = "COLABORADOR";
    private static final String CLAVE_MAX_ASIGNACIONES = "MAX_ASIGNACIONES_POR_COLABORADOR";
    private static final int MAX_ASIGNACIONES_POR_DEFECTO = 3;

    // Directorio y búsqueda de candidatos (TASK-029): filtros GET y paginación en el servidor.
    public static final int TAMANIO_PAGINA = 6;
    // Directorio: "0" es "Sin disponibilidad" (0 h); el resto, "N h/sem o más".
    public static final List<String> OPCIONES_DISPONIBILIDAD = List.of("24", "16", "8", "0");
    // Directorio: número exacto de asignaciones activas o "max" (carga máxima).
    public static final List<String> OPCIONES_CARGA = List.of("0", "1", "2", "max");
    public static final List<String> OPCIONES_NIVEL = List.of("Junior", "Semi Senior", "Senior");
    // Candidatos: disponibilidad mínima; "0" es "Todas" (horas >= 0).
    public static final List<String> OPCIONES_DISPONIBILIDAD_MINIMA = List.of("0", "8", "16", "24");
    // Candidatos: "available" = puede recibir otra; "full" = límite alcanzado.
    public static final List<String> OPCIONES_CARGA_CANDIDATO = List.of("available", "full");
    // Umbral de la tarjeta "Disponibles" del directorio.
    private static final BigDecimal HORAS_DISPONIBLE = BigDecimal.valueOf(16);
    private static final int LONGITUD_MAXIMA_BUSQUEDA = 100;
    // Igual que toLocaleLowerCase("es") del JS anterior: sin mayúsculas, con tildes.
    private static final Locale LOCALE_BUSQUEDA = Locale.forLanguageTag("es");

    /** Filtros ya validados del directorio: los valores nulos significan "Todos". */
    public record FiltrosColaborador(String busqueda, String disponibilidad, String carga, String nivel) {
    }

    /** Filtros ya validados de la búsqueda de candidatos: disponibilidad "0" y carga nula son "Todas". */
    public record FiltrosCandidato(String busqueda, String disponibilidad, String carga) {
    }

    /** Contadores de las tarjetas del directorio sobre todos los colaboradores activos. */
    public record ContadoresColaboradores(long total, long disponibles, long sinAsignaciones, long cargaMaxima) {
    }

    public record PaginaColaboradores(List<RmColaboradorResumen> filas, int paginaActual, int totalPaginas,
                                      long totalRegistros, ContadoresColaboradores contadores) {
    }

    public record PaginaCandidatos(List<CandidatoConCosto> filas, int paginaActual, int totalPaginas,
                                   long totalRegistros) {
    }

    private record ColaboradorConResumen(Usuario colaborador, RmColaboradorResumen resumen) {
    }

    private record Recorte<T>(List<T> filas, int paginaActual, int totalPaginas) {
    }

    private final UsuarioRepository usuarioRepository;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    private final AsignacionRepository asignacionRepository;
    private final CertificadoRepository certificadoRepository;
    private final ConfiguracionSistemaRepository configuracionSistemaRepository;
    private final ExperienciaProfesionalRepository experienciaProfesionalRepository;
    private final EducacionRepository educacionRepository;
    private final RmPresupuestoService presupuestoService;

    public RmColaboradorConsultaService(
            UsuarioRepository usuarioRepository,
            ColaboradorHabilidadRepository colaboradorHabilidadRepository,
            AsignacionRepository asignacionRepository,
            CertificadoRepository certificadoRepository,
            ConfiguracionSistemaRepository configuracionSistemaRepository,
            ExperienciaProfesionalRepository experienciaProfesionalRepository,
            EducacionRepository educacionRepository,
            RmPresupuestoService presupuestoService) {
        this.usuarioRepository = usuarioRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.asignacionRepository = asignacionRepository;
        this.certificadoRepository = certificadoRepository;
        this.configuracionSistemaRepository = configuracionSistemaRepository;
        this.experienciaProfesionalRepository = experienciaProfesionalRepository;
        this.educacionRepository = educacionRepository;
        this.presupuestoService = presupuestoService;
    }

    // Candidato + costo estimado para un proyecto concreto (sección 11 de la
    // especificación de presupuesto). Solo se usa en la pantalla de búsqueda
    // de candidatos del RM — el listado general de colaboradores no expone sueldo.
    public record CandidatoConCosto(RmColaboradorResumen resumen, java.math.BigDecimal sueldoBase,
                                     java.math.BigDecimal horasContratadas,
                                     RmPresupuestoService.CostoAsignacion costoEstimado, boolean cabeEnPresupuesto) {
    }

    // Usado por la pantalla "Proponer asignación" para calcular el costo en
    // vivo en el navegador (JS) al elegir colaborador + horas.
    @Transactional(readOnly = true)
    public java.util.Map<Long, BigDecimal> mapaSueldosBase() {
        return usuarioRepository.findActivosByRolNombre(ROL_COLABORADOR).stream()
                .collect(java.util.stream.Collectors.toMap(Usuario::getId,
                        u -> u.getSueldoBase() == null ? BigDecimal.ZERO : u.getSueldoBase()));
    }

    /**
     * Normaliza los parámetros GET del directorio. Vacíos, "all" o valores desconocidos
     * significan "Todos" (null); la búsqueda se recorta a 100 caracteres.
     */
    public FiltrosColaborador normalizarFiltrosDirectorio(
            String busqueda, String disponibilidad, String carga, String nivel) {
        return new FiltrosColaborador(
                normalizarBusqueda(busqueda),
                opcionValida(disponibilidad, OPCIONES_DISPONIBILIDAD),
                opcionValida(carga, OPCIONES_CARGA),
                opcionValida(nivel, OPCIONES_NIVEL));
    }

    /**
     * Normaliza los parámetros GET de la búsqueda de candidatos. Disponibilidad vacía o
     * desconocida vuelve a "0" (Todas); carga vacía, "all" o desconocida es Todas (null).
     */
    public FiltrosCandidato normalizarFiltrosCandidatos(String busqueda, String disponibilidad, String carga) {
        String minimo = opcionValida(disponibilidad, OPCIONES_DISPONIBILIDAD_MINIMA);
        return new FiltrosCandidato(
                normalizarBusqueda(busqueda),
                minimo == null ? "0" : minimo,
                opcionValida(carga, OPCIONES_CARGA_CANDIDATO));
    }

    /**
     * Filtra, cuenta y pagina el directorio en el orden de findActivosByRolNombre (nombre y
     * apellido). Los contadores se calculan sobre todos los colaboradores activos.
     */
    @Transactional(readOnly = true)
    public PaginaColaboradores listarPaginaDirectorio(FiltrosColaborador filtros, String pagina) {
        List<RmColaboradorResumen> todos = listarColaboradoresActivos();
        Predicate<RmColaboradorResumen> busqueda = coincideBusqueda(filtros.busqueda());
        List<RmColaboradorResumen> filtrados = todos.stream()
                .filter(busqueda)
                .filter(resumen -> coincideDisponibilidadDirectorio(resumen, filtros.disponibilidad()))
                .filter(resumen -> coincideCargaDirectorio(resumen, filtros.carga()))
                .filter(resumen -> filtros.nivel() == null || filtros.nivel().equals(resumen.getNivel()))
                .toList();
        Recorte<RmColaboradorResumen> recorte = recortar(filtrados, pagina);
        ContadoresColaboradores contadores = new ContadoresColaboradores(
                todos.size(),
                todos.stream().filter(resumen -> resumen.getHorasDisponibles().compareTo(HORAS_DISPONIBLE) >= 0).count(),
                todos.stream().filter(resumen -> resumen.getAsignacionesActivas() == 0).count(),
                todos.stream().filter(RmColaboradorResumen::isCargaMaxima).count());
        return new PaginaColaboradores(recorte.filas(), recorte.paginaActual(), recorte.totalPaginas(),
                filtrados.size(), contadores);
    }

    /**
     * Candidatos de un proyecto: todos los colaboradores activos, en el mismo orden (quienes ya
     * están en el proyecto se siguen listando y la plantilla bloquea "Proponer"). Filtra sobre el
     * resumen validado y calcula el costo estimado solo para las filas de la página.
     */
    @Transactional(readOnly = true)
    public PaginaCandidatos listarPaginaCandidatos(Proyecto proyecto, FiltrosCandidato filtros, String pagina) {
        int maxAsignaciones = obtenerMaxAsignaciones();
        BigDecimal minimo = new BigDecimal(filtros.disponibilidad() == null ? "0" : filtros.disponibilidad());
        Predicate<RmColaboradorResumen> busqueda = coincideBusqueda(filtros.busqueda());

        List<ColaboradorConResumen> filtrados = usuarioRepository.findActivosByRolNombre(ROL_COLABORADOR).stream()
                .map(colaborador -> new ColaboradorConResumen(colaborador, crearResumen(colaborador, maxAsignaciones)))
                .filter(item -> busqueda.test(item.resumen()))
                .filter(item -> item.resumen().getHorasDisponibles().compareTo(minimo) >= 0)
                .filter(item -> coincideCargaCandidato(item.resumen(), filtros.carga()))
                .toList();
        Recorte<ColaboradorConResumen> recorte = recortar(filtrados, pagina);

        RmPresupuestoService.ResumenPresupuesto resumenPresupuesto = presupuestoService.calcularResumen(proyecto);
        List<CandidatoConCosto> filas = recorte.filas().stream()
                .map(item -> {
                    Usuario colaborador = item.colaborador();
                    RmPresupuestoService.CostoAsignacion costo = presupuestoService.calcularCosto(
                            proyecto, colaborador, proyecto.getHorasSemanalesRequeridas());
                    boolean cabe = costo.calculable()
                            && costo.costoTotal().compareTo(resumenPresupuesto.disponible()) <= 0;
                    return new CandidatoConCosto(item.resumen(), colaborador.getSueldoBase(),
                            colaborador.getHorasContratadasSemana(), costo, cabe);
                })
                .toList();
        return new PaginaCandidatos(filas, recorte.paginaActual(), recorte.totalPaginas(), filtrados.size());
    }

    @Transactional(readOnly = true)
    public List<RmColaboradorResumen> listarColaboradoresActivos() {
        int maxAsignaciones = obtenerMaxAsignaciones();

        return usuarioRepository.findActivosByRolNombre(ROL_COLABORADOR)
                .stream()
                .map(colaborador -> crearResumen(colaborador, maxAsignaciones))
                .toList();
    }

    @Transactional(readOnly = true)
    public RmColaboradorDetalle obtenerDetalle(Long colaboradorId) {
        Usuario colaborador = usuarioRepository.findById(colaboradorId)
                .filter(Usuario::isActivo)
                .filter(usuario -> ROL_COLABORADOR.equals(usuario.getRol().getNombre()))
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el colaborador solicitado."));

        int maxAsignaciones = obtenerMaxAsignaciones();
        RmColaboradorResumen resumen = crearResumen(colaborador, maxAsignaciones);

        // Perfil completo: todas las habilidades activas, con su estado de validación.
        List<RmColaboradorDetalle.HabilidadDetalle> habilidades = obtenerHabilidadesPerfil(colaborador)
                .stream()
                .map(item -> new RmColaboradorDetalle.HabilidadDetalle(
                        item.getHabilidad().getNombre(),
                        textoEnum(item.getNivelDominio().name()),
                        textoEnum(item.getEstadoValidacion().name()),
                        item.getEstadoValidacion().name()))
                .toList();

        List<RmColaboradorDetalle.AsignacionDetalle> asignaciones = obtenerAsignacionesActivas(colaborador)
                .stream()
                .map(asignacion -> new RmColaboradorDetalle.AsignacionDetalle(
                        asignacion.getId(),
                        asignacion.getProyecto().getNombre(),
                        asignacion.getHorasSemanales(),
                        textoEnum(asignacion.getEstado().name()),
                        asignacion.getProyecto().getFechaInicio(),
                        asignacion.getProyecto().getFechaFinEstimada()))
                .toList();

        List<RmColaboradorDetalle.ExperienciaDetalle> experiencias =
                experienciaProfesionalRepository.findByColaboradorOrderByFechaInicioDesc(colaborador)
                        .stream()
                        .map(this::crearExperiencia)
                        .toList();

        List<RmColaboradorDetalle.EducacionDetalle> educacion =
                educacionRepository.findByColaboradorAndActivoTrueOrderByFechaInicioDesc(colaborador)
                        .stream()
                        .map(this::crearEducacion)
                        .toList();

        return new RmColaboradorDetalle(
                resumen,
                colaborador.getCorreo(),
                colaborador.getTelefono(),
                colaborador.getDescripcion(),
                colaborador.getHorasContratadasSemana(),
                colaborador.getFechaContratacion(),
                habilidades,
                asignaciones,
                experiencias,
                educacion);
    }

    // El resumen alimenta tarjetas, texto de búsqueda y propuestas: solo habilidades validadas.
    private RmColaboradorResumen crearResumen(Usuario colaborador, int maxAsignaciones) {
        List<String> habilidades = obtenerHabilidadesValidadas(colaborador)
                .stream()
                .map(item -> item.getHabilidad().getNombre())
                .toList();

        int asignacionesActivas = obtenerAsignacionesActivas(colaborador).size();
        long certificadosAprobados = certificadoRepository
                .countByColaboradorAndEstado(colaborador, EstadoCertificado.APROBADO);

        NivelExperiencia nivel = colaborador.getNivelExperiencia();
        String nivelCodigo = nivel == null ? "SIN_DEFINIR" : nivel.name();

        return new RmColaboradorResumen(
                colaborador.getId(),
                nombreCompleto(colaborador),
                iniciales(colaborador),
                colaborador.getFotoUrl(),
                valorOAlternativa(colaborador.getCargo() != null ? colaborador.getCargo().getNombre() : null, "Cargo sin registrar"),
                nivel == null ? "Sin definir" : textoNivel(nivel),
                nivelCodigo,
                habilidades,
                colaborador.getHorasDisponibles() == null ? BigDecimal.ZERO : colaborador.getHorasDisponibles(),
                colaborador.getAniosExperiencia(),
                asignacionesActivas,
                maxAsignaciones,
                certificadosAprobados);
    }

    // Para buscar, recomendar y proponer solo cuentan las habilidades VALIDADA (TASK-015).
    private List<ColaboradorHabilidad> obtenerHabilidadesValidadas(Usuario colaborador) {
        return ordenarPorNombre(colaboradorHabilidadRepository
                .findByColaboradorAndActivoTrueAndEstadoValidacion(colaborador, EstadoValidacion.VALIDADA));
    }

    // Perfil e historial: todas las habilidades activas (validadas, pendientes y rechazadas).
    private List<ColaboradorHabilidad> obtenerHabilidadesPerfil(Usuario colaborador) {
        return ordenarPorNombre(colaboradorHabilidadRepository.findByColaboradorAndActivoTrue(colaborador));
    }

    private List<ColaboradorHabilidad> ordenarPorNombre(List<ColaboradorHabilidad> habilidades) {
        return habilidades.stream()
                .sorted(Comparator.comparing(item -> item.getHabilidad().getNombre(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<Asignacion> obtenerAsignacionesActivas(Usuario colaborador) {
        return asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA);
    }

    // Búsqueda sobre getTextoBusqueda() del resumen: nombre, cargo, nivel y solo habilidades VALIDADA.
    private Predicate<RmColaboradorResumen> coincideBusqueda(String busqueda) {
        String buscado = busqueda == null ? "" : busqueda.toLowerCase(LOCALE_BUSQUEDA);
        return resumen -> buscado.isEmpty()
                || resumen.getTextoBusqueda().toLowerCase(LOCALE_BUSQUEDA).contains(buscado);
    }

    // "0" = exactamente 0 h; otro umbral = horas >= umbral (criterio del JS anterior del directorio).
    private boolean coincideDisponibilidadDirectorio(RmColaboradorResumen resumen, String disponibilidad) {
        if (disponibilidad == null) return true;
        BigDecimal umbral = new BigDecimal(disponibilidad);
        return umbral.signum() == 0
                ? resumen.getHorasDisponibles().signum() == 0
                : resumen.getHorasDisponibles().compareTo(umbral) >= 0;
    }

    private boolean coincideCargaDirectorio(RmColaboradorResumen resumen, String carga) {
        if (carga == null) return true;
        if ("max".equals(carga)) return resumen.isCargaMaxima();
        return resumen.getAsignacionesActivas() == Integer.parseInt(carga);
    }

    // "available": activas < máximo; "full": activas >= máximo (criterio del JS anterior).
    private boolean coincideCargaCandidato(RmColaboradorResumen resumen, String carga) {
        if (carga == null) return true;
        boolean limiteAlcanzado = resumen.getAsignacionesActivas() >= resumen.getMaxAsignaciones();
        return "full".equals(carga) == limiteAlcanzado;
    }

    private String normalizarBusqueda(String busqueda) {
        String limpia = busqueda == null ? "" : busqueda.trim();
        if (limpia.length() > LONGITUD_MAXIMA_BUSQUEDA) limpia = limpia.substring(0, LONGITUD_MAXIMA_BUSQUEDA);
        return limpia.isEmpty() ? null : limpia;
    }

    private String opcionValida(String valor, List<String> opciones) {
        if (valor == null) return null;
        return opciones.stream()
                .filter(opcion -> opcion.equalsIgnoreCase(valor.trim()))
                .findFirst().orElse(null);
    }

    // Página desde 1: no numérica, 0 o negativa → 1; mayor que el total → última.
    private <T> Recorte<T> recortar(List<T> filtrados, String pagina) {
        int totalPaginas = Math.max(1, (int) Math.ceil(filtrados.size() / (double) TAMANIO_PAGINA));
        int paginaActual = Math.min(Math.max(1, numeroPagina(pagina)), totalPaginas);
        int desde = (paginaActual - 1) * TAMANIO_PAGINA;
        int hasta = Math.min(desde + TAMANIO_PAGINA, filtrados.size());
        return new Recorte<>(desde < hasta ? filtrados.subList(desde, hasta) : List.of(),
                paginaActual, totalPaginas);
    }

    private int numeroPagina(String pagina) {
        if (pagina == null) return 1;
        try {
            return Integer.parseInt(pagina.trim());
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private int obtenerMaxAsignaciones() {
        return configuracionSistemaRepository.findByClave(CLAVE_MAX_ASIGNACIONES)
                .map(configuracion -> parsearEnteroPositivo(configuracion.getValor()))
                .orElse(MAX_ASIGNACIONES_POR_DEFECTO);
    }

    private int parsearEnteroPositivo(String valor) {
        try {
            int numero = Integer.parseInt(valor);
            return numero > 0 ? numero : MAX_ASIGNACIONES_POR_DEFECTO;
        } catch (NumberFormatException ex) {
            return MAX_ASIGNACIONES_POR_DEFECTO;
        }
    }

    private RmColaboradorDetalle.ExperienciaDetalle crearExperiencia(ExperienciaProfesional experiencia) {
        return new RmColaboradorDetalle.ExperienciaDetalle(
                valorOAlternativa(experiencia.getEmpresa(), "Empresa sin registrar"),
                valorOAlternativa(experiencia.getCargo(), "Cargo sin registrar"),
                experiencia.getDescripcion(),
                experiencia.getFechaInicio(),
                experiencia.getFechaFin(),
                experiencia.isActual());
    }

    private RmColaboradorDetalle.EducacionDetalle crearEducacion(Educacion educacion) {
        return new RmColaboradorDetalle.EducacionDetalle(
                educacion.getInstitucion(),
                educacion.getTitulo(),
                educacion.getFechaInicio(),
                educacion.getFechaFin(),
                educacion.isActual(),
                textoEnum(educacion.getEstado().name()),
                educacion.getArchivoUrl());
    }

    private String nombreCompleto(Usuario usuario) {
        String nombre = valorOAlternativa(usuario.getNombre(), "");
        String apellido = valorOAlternativa(usuario.getApellido(), "");
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? usuario.getCorreo() : completo;
    }

    private String iniciales(Usuario usuario) {
        String nombre = valorOAlternativa(usuario.getNombre(), "").trim();
        String apellido = valorOAlternativa(usuario.getApellido(), "").trim();
        String iniciales = (nombre.isEmpty() ? "" : nombre.substring(0, 1))
                + (apellido.isEmpty() ? "" : apellido.substring(0, 1));
        return iniciales.isEmpty() ? "CO" : iniciales.toUpperCase();
    }

    private String valorOAlternativa(String valor, String alternativa) {
        return valor == null || valor.isBlank() ? alternativa : valor;
    }

    private String textoEnum(String valor) {
        String texto = valor.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private String textoNivel(NivelExperiencia nivel) {
        return nivel == NivelExperiencia.SEMI_SENIOR ? "Semi Senior" : textoEnum(nivel.name());
    }
}
