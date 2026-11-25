package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.CategoriaHabilidadRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.util.Paginador;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Épica 5 (Catálogo de habilidades). El Admin administra tanto las habilidades como sus categorías.
 */
@Service
public class AdminHabilidadService {

    private static final Map<String, String> CATEGORIA_A_BADGE = Map.of(
            "Técnico", "bg-blue-lt text-blue",
            "Habilidades blandas", "bg-purple-lt text-purple",
            "Certificación", "bg-orange-lt text-orange",
            "Herramientas", "bg-secondary-lt text-secondary"
    );

    /**
     * Fila del catálogo de habilidades.
     *
     * @param id id del registro
     * @param nombre nombre ingresado
     * @param categoriaId id de la categoría
     * @param categoriaNombre nombre de la categoría
     * @param categoriaBadgeClase clase CSS de la etiqueta de la categoría
     * @param colaboradores colaboradores que la registran
     * @param activa {@code true} si está activa
     */
    public record HabilidadFila(Long id, String nombre, String categoriaId, String categoriaNombre,
                                 String categoriaBadgeClase, long colaboradores, boolean activa) {
    }

    /**
     * Fila de la tabla de categorías.
     *
     * @param id id del registro
     * @param nombre nombre ingresado
     * @param descripcion descripción; puede ser {@code null} o vacía
     * @param badgeClase clase CSS de la etiqueta
     * @param habilidades habilidades de la categoría
     * @param activa {@code true} si está activa
     */
    public record CategoriaFila(Long id, String nombre, String descripcion, String badgeClase,
                                 long habilidades, boolean activa) {
    }

    /**
     * Totales de las tarjetas del catálogo.
     *
     * @param activas habilidades activas
     * @param categorias categorías activas
     * @param desactivadas habilidades desactivadas
     * @param masSolicitadaNombre habilidad más registrada
     * @param masSolicitadaCount colaboradores que registran la más solicitada
     */
    public record ResumenHabilidades(long activas, long categorias, long desactivadas,
                                      String masSolicitadaNombre, long masSolicitadaCount) {
    }

    private final HabilidadRepository habilidadRepository;
    private final CategoriaHabilidadRepository categoriaHabilidadRepository;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    private final AuditoriaService auditoriaService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public AdminHabilidadService(HabilidadRepository habilidadRepository,
                                  CategoriaHabilidadRepository categoriaHabilidadRepository,
                                  ColaboradorHabilidadRepository colaboradorHabilidadRepository,
                                  AuditoriaService auditoriaService) {
        this.habilidadRepository = habilidadRepository;
        this.categoriaHabilidadRepository = categoriaHabilidadRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lista las categorías activas por nombre.
     *
     * @return categorías activas
     */
    @Transactional(readOnly = true)
    public List<CategoriaHabilidad> listarCategoriasActivas() {
        return categoriaHabilidadRepository.findByActivaTrue().stream()
                .sorted(Comparator.comparing(CategoriaHabilidad::getNombre))
                .toList();
    }

    /**
     * Lista todas las categorías con la cantidad de habilidades de cada una.
     *
     * @return filas de categorías
     */
    @Transactional(readOnly = true)
    public List<CategoriaFila> listarCategorias() {
        return categoriaHabilidadRepository.findAll().stream()
                .sorted(Comparator.comparing(CategoriaHabilidad::getNombre))
                .map(c -> new CategoriaFila(c.getId(), c.getNombre(), c.getDescripcion(),
                        CATEGORIA_A_BADGE.getOrDefault(c.getNombre(), "bg-secondary-lt text-secondary"),
                        habilidadRepository.countByCategoria_Id(c.getId()), c.isActiva()))
                .toList();
    }

    /**
     * Crea una categoría y la registra en la auditoría.
     *
     * @param nombre nombre ingresado
     * @param descripcion descripción; puede ser {@code null} o vacía
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si el nombre está vacío o ya existe
     */
    @Transactional
    public void crearCategoria(String nombre, String descripcion, Usuario admin) {
        String nombreLimpio = nombre != null ? nombre.trim() : "";
        if (nombreLimpio.isEmpty()) {
            throw new IllegalArgumentException("El nombre de la categoría no puede estar vacío.");
        }
        if (categoriaHabilidadRepository.findByNombreIgnoreCase(nombreLimpio).isPresent()) {
            throw new IllegalArgumentException("Ya existe la categoría \"" + nombreLimpio + "\".");
        }

        CategoriaHabilidad categoria = new CategoriaHabilidad();
        categoria.setNombre(nombreLimpio);
        categoria.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion.trim() : null);
        categoria.setActiva(true);
        categoria = categoriaHabilidadRepository.save(categoria);

        auditoriaService.registrar(admin, "CREAR_CATEGORIA", "CATEGORIA_HABILIDAD", categoria.getId(),
                "Creó la categoría \"" + nombreLimpio + "\".");
    }

    /**
     * Cambia el nombre y la descripción de una categoría.
     *
     * @param categoriaId id de la categoría
     * @param nombre nombre ingresado
     * @param descripcion descripción; puede ser {@code null} o vacía
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si no existe, o el nombre está vacío o repetido
     */
    @Transactional
    public void editarCategoria(Long categoriaId, String nombre, String descripcion, Usuario admin) {
        String nombreLimpio = nombre != null ? nombre.trim() : "";
        if (nombreLimpio.isEmpty()) {
            throw new IllegalArgumentException("El nombre de la categoría no puede estar vacío.");
        }
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada."));

        categoriaHabilidadRepository.findByNombreIgnoreCase(nombreLimpio)
                .filter(c -> !c.getId().equals(categoriaId))
                .ifPresent(c -> {
                    throw new IllegalArgumentException("Ya existe la categoría \"" + nombreLimpio + "\".");
                });

        String nombreAnterior = categoria.getNombre();
        categoria.setNombre(nombreLimpio);
        categoria.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion.trim() : null);
        categoriaHabilidadRepository.save(categoria);

        auditoriaService.registrar(admin, "EDITAR_CATEGORIA", "CATEGORIA_HABILIDAD", categoria.getId(),
                "Editó la categoría.", nombreAnterior, nombreLimpio, null);
    }

    /**
     * Desactiva una categoría.
     *
     * @param categoriaId id de la categoría
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si no existe o todavía tiene habilidades
     */
    @Transactional
    public void desactivarCategoria(Long categoriaId, Usuario admin) {
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada."));
        if (habilidadRepository.countByCategoria_Id(categoriaId) > 0) {
            throw new IllegalArgumentException(
                    "No puedes desactivar \"" + categoria.getNombre() + "\": todavía tiene habilidades asociadas.");
        }
        categoria.setActiva(false);
        categoriaHabilidadRepository.save(categoria);
        auditoriaService.registrar(admin, "DESACTIVAR_CATEGORIA", "CATEGORIA_HABILIDAD", categoria.getId(),
                "Desactivó la categoría \"" + categoria.getNombre() + "\".");
    }

    /**
     * Reactiva una categoría.
     *
     * @param categoriaId id de la categoría
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si no existe
     */
    @Transactional
    public void reactivarCategoria(Long categoriaId, Usuario admin) {
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada."));
        categoria.setActiva(true);
        categoriaHabilidadRepository.save(categoria);
        auditoriaService.registrar(admin, "REACTIVAR_CATEGORIA", "CATEGORIA_HABILIDAD", categoria.getId(),
                "Reactivó la categoría \"" + categoria.getNombre() + "\".");
    }

    /**
     * Lista todas las habilidades por nombre.
     *
     * @return filas del catálogo
     */
    @Transactional(readOnly = true)
    public List<HabilidadFila> listar() {
        return habilidadRepository.findAll().stream()
                .sorted(Comparator.comparing(Habilidad::getNombre))
                .map(this::aFila)
                .toList();
    }

    /** Catálogo filtrado y paginado en el servidor (antes lo hacía admin-habilidades.js). */
    @Transactional(readOnly = true)
    public Page<HabilidadFila> listarPagina(String busqueda, String categoria, String estado, Integer pagina) {
        List<HabilidadFila> filtradas = listar().stream()
                .filter(h -> Paginador.contiene(h.nombre(), busqueda))
                .filter(h -> Paginador.coincide(h.categoriaNombre(), categoria))
                .filter(h -> Paginador.coincide(h.activa() ? "Activa" : "Desactivada", estado))
                .toList();
        return Paginador.paginar(filtradas, pagina);
    }

    /**
     * Calcula los totales del catálogo y la habilidad más registrada.
     *
     * @return totales del catálogo
     */
    @Transactional(readOnly = true)
    public ResumenHabilidades resumen() {
        List<HabilidadFila> filas = listar();
        long activas = filas.stream().filter(HabilidadFila::activa).count();
        long desactivadas = filas.size() - activas;
        long categorias = categoriaHabilidadRepository.findByActivaTrue().size();

        HabilidadFila masSolicitada = filas.stream()
                .max(Comparator.comparingLong(HabilidadFila::colaboradores))
                .orElse(null);

        return new ResumenHabilidades(activas, categorias, desactivadas,
                masSolicitada != null ? masSolicitada.nombre() : "—",
                masSolicitada != null ? masSolicitada.colaboradores() : 0);
    }

    /**
     * Crea una habilidad y la registra en la auditoría.
     *
     * @param nombre nombre ingresado
     * @param categoriaId id de la categoría
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si el nombre está vacío, es muy largo o ya existe, o la categoría no es
     *         válida
     */
    @Transactional
    public void crear(String nombre, Long categoriaId, Usuario admin) {
        String nombreLimpio = nombre.trim();
        if (nombreLimpio.isEmpty()) {
            throw new IllegalArgumentException("El nombre de la habilidad no puede estar vacío.");
        }
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría inválida."));

        validarLargo(nombreLimpio);
        if (habilidadRepository.findFirstByNombreIgnoreCaseOrderByIdAsc(nombreLimpio).isPresent()) {
            throw new IllegalArgumentException("Ya existe la habilidad \"" + nombreLimpio + "\" en el catálogo (no puede repetirse en otra categoría).");
        }

        Habilidad habilidad = new Habilidad();
        habilidad.setNombre(nombreLimpio);
        habilidad.setCategoria(categoria);
        habilidad.setActiva(true);
        habilidad = habilidadRepository.save(habilidad);

        auditoriaService.registrar(admin, "CREAR_HABILIDAD", "HABILIDAD", habilidad.getId(),
                "Creó la habilidad \"" + nombreLimpio + "\" en la categoría " + categoria.getNombre() + ".");
    }

    /**
     * Cambia el nombre y la categoría de una habilidad.
     *
     * @param habilidadId id de la habilidad
     * @param nombre nombre ingresado
     * @param categoriaId id de la categoría
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si no existe, o el nombre está vacío, es muy largo o ya existe
     */
    @Transactional
    public void editar(Long habilidadId, String nombre, Long categoriaId, Usuario admin) {
        String nombreLimpio = nombre.trim();
        if (nombreLimpio.isEmpty()) {
            throw new IllegalArgumentException("El nombre de la habilidad no puede estar vacío.");
        }
        Habilidad habilidad = habilidadRepository.findById(habilidadId)
                .orElseThrow(() -> new IllegalArgumentException("Habilidad no encontrada."));
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría inválida."));

        validarLargo(nombreLimpio);
        // Solo se revisa si el nombre cambió: así se puede cambiar la categoría de una habilidad aunque la base
        // tenga datos antiguos con ese mismo nombre repetido en otra categoría.
        boolean cambiaNombre = !habilidad.getNombre().equalsIgnoreCase(nombreLimpio);
        if (cambiaNombre && habilidadRepository.existsByNombreIgnoreCaseAndIdNot(nombreLimpio, habilidadId)) {
            throw new IllegalArgumentException("Ya existe la habilidad \"" + nombreLimpio + "\" en el catálogo (no puede repetirse en otra categoría).");
        }

        String nombreAnterior = habilidad.getNombre();
        habilidad.setNombre(nombreLimpio);
        habilidad.setCategoria(categoria);
        habilidadRepository.save(habilidad);

        auditoriaService.registrar(admin, "EDITAR_HABILIDAD", "HABILIDAD", habilidad.getId(),
                "Editó la habilidad.", nombreAnterior, nombreLimpio, null);
    }

    /**
     * Desactiva una habilidad; se conserva en los perfiles que ya la tienen.
     *
     * @param habilidadId id de la habilidad
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si no existe
     */
    @Transactional
    public void desactivar(Long habilidadId, Usuario admin) {
        Habilidad habilidad = habilidadRepository.findById(habilidadId)
                .orElseThrow(() -> new IllegalArgumentException("Habilidad no encontrada."));
        habilidad.setActiva(false);
        habilidadRepository.save(habilidad);
        auditoriaService.registrar(admin, "DESACTIVAR_HABILIDAD", "HABILIDAD", habilidad.getId(),
                "Desactivó la habilidad \"" + habilidad.getNombre() + "\".");
    }

    /**
     * Reactiva una habilidad.
     *
     * @param habilidadId id de la habilidad
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si no existe
     */
    @Transactional
    public void reactivar(Long habilidadId, Usuario admin) {
        Habilidad habilidad = habilidadRepository.findById(habilidadId)
                .orElseThrow(() -> new IllegalArgumentException("Habilidad no encontrada."));
        habilidad.setActiva(true);
        habilidadRepository.save(habilidad);
        auditoriaService.registrar(admin, "REACTIVAR_HABILIDAD", "HABILIDAD", habilidad.getId(),
                "Reactivó la habilidad \"" + habilidad.getNombre() + "\".");
    }

    /**
     * Comprueba que el nombre no pase de 100 caracteres.
     * <p>
     * La columna es VARCHAR(100): sin este control, un nombre largo terminaba en un error sin explicación.
     *
     * @param nombre nombre ingresado
     * @throws IllegalArgumentException si es más largo
     */
    private static void validarLargo(String nombre) {
        if (nombre.length() > 100) {
            throw new IllegalArgumentException("El nombre de la habilidad no puede superar los 100 caracteres.");
        }
    }

    /**
     * Convierte una habilidad en una fila del catálogo.
     *
     * @param h habilidad
     * @return fila para la vista
     */
    private HabilidadFila aFila(Habilidad h) {
        String categoriaNombre = h.getCategoria().getNombre();
        long colaboradores = colaboradorHabilidadRepository.countByHabilidadAndActivoTrue(h);
        return new HabilidadFila(h.getId(), h.getNombre(), h.getCategoria().getId().toString(), categoriaNombre,
                CATEGORIA_A_BADGE.getOrDefault(categoriaNombre, "bg-secondary-lt text-secondary"),
                colaboradores, h.isActiva());
    }
}
