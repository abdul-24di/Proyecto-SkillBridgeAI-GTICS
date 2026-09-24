package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.CategoriaHabilidadRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Épica 5 (Catálogo de habilidades). El Admin administra tanto las
// habilidades como sus categorías.
@Service
public class AdminHabilidadService {

    private static final Map<String, String> CATEGORIA_A_BADGE = Map.of(
            "Técnico", "bg-blue-lt text-blue",
            "Habilidades blandas", "bg-purple-lt text-purple",
            "Certificación", "bg-orange-lt text-orange",
            "Herramientas", "bg-secondary-lt text-secondary"
    );

    public record HabilidadFila(Long id, String nombre, String categoriaId, String categoriaNombre,
                                 String categoriaBadgeClase, long colaboradores, boolean activa) {
    }

    public record CategoriaFila(Long id, String nombre, String descripcion, String badgeClase,
                                 long habilidades, boolean activa) {
    }

    public record ResumenHabilidades(long activas, long categorias, long desactivadas,
                                      String masSolicitadaNombre, long masSolicitadaCount) {
    }

    private final HabilidadRepository habilidadRepository;
    private final CategoriaHabilidadRepository categoriaHabilidadRepository;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    private final AuditoriaService auditoriaService;

    public AdminHabilidadService(HabilidadRepository habilidadRepository,
                                  CategoriaHabilidadRepository categoriaHabilidadRepository,
                                  ColaboradorHabilidadRepository colaboradorHabilidadRepository,
                                  AuditoriaService auditoriaService) {
        this.habilidadRepository = habilidadRepository;
        this.categoriaHabilidadRepository = categoriaHabilidadRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<CategoriaHabilidad> listarCategoriasActivas() {
        return categoriaHabilidadRepository.findByActivaTrue().stream()
                .sorted(Comparator.comparing(CategoriaHabilidad::getNombre))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoriaFila> listarCategorias() {
        return categoriaHabilidadRepository.findAll().stream()
                .sorted(Comparator.comparing(CategoriaHabilidad::getNombre))
                .map(c -> new CategoriaFila(c.getId(), c.getNombre(), c.getDescripcion(),
                        CATEGORIA_A_BADGE.getOrDefault(c.getNombre(), "bg-secondary-lt text-secondary"),
                        habilidadRepository.countByCategoria_Id(c.getId()), c.isActiva()))
                .toList();
    }

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

    @Transactional
    public void reactivarCategoria(Long categoriaId, Usuario admin) {
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada."));
        categoria.setActiva(true);
        categoriaHabilidadRepository.save(categoria);
        auditoriaService.registrar(admin, "REACTIVAR_CATEGORIA", "CATEGORIA_HABILIDAD", categoria.getId(),
                "Reactivó la categoría \"" + categoria.getNombre() + "\".");
    }

    @Transactional(readOnly = true)
    public List<HabilidadFila> listar() {
        return habilidadRepository.findAll().stream()
                .sorted(Comparator.comparing(Habilidad::getNombre))
                .map(this::aFila)
                .toList();
    }

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

    @Transactional
    public void crear(String nombre, Long categoriaId, Usuario admin) {
        String nombreLimpio = nombre.trim();
        if (nombreLimpio.isEmpty()) {
            throw new IllegalArgumentException("El nombre de la habilidad no puede estar vacío.");
        }
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría inválida."));

        if (habilidadRepository.findByNombreIgnoreCase(nombreLimpio).isPresent()) {
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

        habilidadRepository.findByNombreIgnoreCase(nombreLimpio)
                .filter(h -> !h.getId().equals(habilidadId))
                .ifPresent(h -> {
                    throw new IllegalArgumentException("Ya existe la habilidad \"" + nombreLimpio + "\" en el catálogo (no puede repetirse en otra categoría).");
                });

        String nombreAnterior = habilidad.getNombre();
        habilidad.setNombre(nombreLimpio);
        habilidad.setCategoria(categoria);
        habilidadRepository.save(habilidad);

        auditoriaService.registrar(admin, "EDITAR_HABILIDAD", "HABILIDAD", habilidad.getId(),
                "Editó la habilidad.", nombreAnterior, nombreLimpio, null);
    }

    @Transactional
    public void desactivar(Long habilidadId, Usuario admin) {
        Habilidad habilidad = habilidadRepository.findById(habilidadId)
                .orElseThrow(() -> new IllegalArgumentException("Habilidad no encontrada."));
        habilidad.setActiva(false);
        habilidadRepository.save(habilidad);
        auditoriaService.registrar(admin, "DESACTIVAR_HABILIDAD", "HABILIDAD", habilidad.getId(),
                "Desactivó la habilidad \"" + habilidad.getNombre() + "\".");
    }

    @Transactional
    public void reactivar(Long habilidadId, Usuario admin) {
        Habilidad habilidad = habilidadRepository.findById(habilidadId)
                .orElseThrow(() -> new IllegalArgumentException("Habilidad no encontrada."));
        habilidad.setActiva(true);
        habilidadRepository.save(habilidad);
        auditoriaService.registrar(admin, "REACTIVAR_HABILIDAD", "HABILIDAD", habilidad.getId(),
                "Reactivó la habilidad \"" + habilidad.getNombre() + "\".");
    }

    private HabilidadFila aFila(Habilidad h) {
        String categoriaNombre = h.getCategoria().getNombre();
        long colaboradores = colaboradorHabilidadRepository.countByHabilidadAndActivoTrue(h);
        return new HabilidadFila(h.getId(), h.getNombre(), h.getCategoria().getId().toString(), categoriaNombre,
                CATEGORIA_A_BADGE.getOrDefault(categoriaNombre, "bg-secondary-lt text-secondary"),
                colaboradores, h.isActiva());
    }
}
