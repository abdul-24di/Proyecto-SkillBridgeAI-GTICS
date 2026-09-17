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

// Épica 5 (Catálogo de habilidades). Las categorías son un catálogo fijo
// (igual que `rol`) — el Admin solo administra las habilidades dentro de
// ellas, no crea categorías nuevas desde la UI.
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

        if (habilidadRepository.findByNombreIgnoreCaseAndCategoria_Id(nombreLimpio, categoriaId).isPresent()) {
            throw new IllegalArgumentException("Ya existe la habilidad \"" + nombreLimpio + "\" en esa categoría.");
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

        habilidadRepository.findByNombreIgnoreCaseAndCategoria_Id(nombreLimpio, categoriaId)
                .filter(h -> !h.getId().equals(habilidadId))
                .ifPresent(h -> {
                    throw new IllegalArgumentException("Ya existe la habilidad \"" + nombreLimpio + "\" en esa categoría.");
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
