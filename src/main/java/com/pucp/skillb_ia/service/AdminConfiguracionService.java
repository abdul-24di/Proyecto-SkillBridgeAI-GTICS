package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.ConfiguracionSistema;
import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.ConfiguracionSistemaRepository;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Épica 5 (Configuración de parámetros globales, C10). Cambios quedan
// auditados vía AuditoriaService — esta clase no reimplementa esa lógica.
@Service
public class AdminConfiguracionService {

    private static final DateTimeFormatter FECHA_FORMATO = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final String ENTIDAD = "CONFIGURACION";

    public record ParametroFila(Long id, String clave, String descripcion, String valor, String ultimaModificacion) {
    }

    public record HistorialFila(String fecha, String clave, String valorAnterior, String valorNuevo, String modificadoPor) {
    }

    private final ConfiguracionSistemaRepository configuracionRepository;
    private final LogAuditoriaRepository logAuditoriaRepository;
    private final AuditoriaService auditoriaService;

    public AdminConfiguracionService(ConfiguracionSistemaRepository configuracionRepository,
                                      LogAuditoriaRepository logAuditoriaRepository,
                                      AuditoriaService auditoriaService) {
        this.configuracionRepository = configuracionRepository;
        this.logAuditoriaRepository = logAuditoriaRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<ParametroFila> listar() {
        List<LogAuditoria> historial = logAuditoriaRepository.findTop20ByEntidadOrderByFechaHoraDesc(ENTIDAD);

        return configuracionRepository.findAll().stream()
                .sorted(Comparator.comparing(ConfiguracionSistema::getClave))
                .map(c -> {
                    Optional<LogAuditoria> ultimo = historial.stream()
                            .filter(l -> l.getEntidadId() != null && l.getEntidadId().equals(c.getId()))
                            .findFirst();
                    String ultimaModificacion = ultimo
                            .map(l -> l.getFechaHora().format(FECHA_FORMATO) + " · "
                                    + (l.getUsuario() != null ? nombreCompleto(l.getUsuario()) : "Sistema"))
                            .orElse("—");
                    return new ParametroFila(c.getId(), c.getClave(), c.getDescripcion(), c.getValor(), ultimaModificacion);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HistorialFila> historialReciente() {
        return logAuditoriaRepository.findTop20ByEntidadOrderByFechaHoraDesc(ENTIDAD).stream()
                .map(l -> new HistorialFila(
                        l.getFechaHora().format(FECHA_FORMATO),
                        l.getDetalle(),
                        l.getValorAnterior(),
                        l.getValorNuevo(),
                        l.getUsuario() != null ? nombreCompleto(l.getUsuario()) : "Sistema"))
                .toList();
    }

    @Transactional
    public void crear(String clave, String descripcion, String valorInicial, Usuario admin) {
        String claveLimpia = clave == null ? "" : clave.trim().toUpperCase().replace(' ', '_');
        String valorLimpio = valorInicial == null ? "" : valorInicial.trim();
        if (claveLimpia.isEmpty()) {
            throw new IllegalArgumentException("La clave del parámetro no puede estar vacía.");
        }
        if (valorLimpio.isEmpty()) {
            throw new IllegalArgumentException("El valor no puede estar vacío.");
        }
        if (claveLimpia.length() > 60) {
            throw new IllegalArgumentException("La clave no puede superar los 60 caracteres.");
        }
        if (!claveLimpia.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("La clave solo puede contener letras, números, espacios y guiones bajos.");
        }
        if (valorLimpio.length() > 255) {
            throw new IllegalArgumentException("El valor no puede superar los 255 caracteres.");
        }
        if (configuracionRepository.findByClave(claveLimpia).isPresent()) {
            throw new IllegalArgumentException("Ya existe un parámetro con la clave \"" + claveLimpia + "\".");
        }

        ConfiguracionSistema config = new ConfiguracionSistema();
        config.setClave(claveLimpia);
        config.setDescripcion(descripcion == null ? "" : descripcion.trim());
        config.setValor(valorLimpio);
        configuracionRepository.save(config);

        auditoriaService.registrar(admin, "CREAR_CONFIGURACION", ENTIDAD, config.getId(),
                config.getClave(), null, valorLimpio, null);
    }

    @Transactional
    public void editar(Long id, String nuevoValor, Usuario admin) {
        String valorLimpio = nuevoValor == null ? "" : nuevoValor.trim();
        if (valorLimpio.isEmpty()) {
            throw new IllegalArgumentException("El valor no puede estar vacío.");
        }

        ConfiguracionSistema config = configuracionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Parámetro no encontrado."));

        String valorAnterior = config.getValor();
        if (valorAnterior != null && valorAnterior.matches("\\d+") && !valorLimpio.matches("\\d+")) {
            throw new IllegalArgumentException("El parámetro \"" + config.getClave() + "\" solo acepta valores numéricos.");
        }
        if (valorLimpio.equals(valorAnterior)) return;

        config.setValor(valorLimpio);
        configuracionRepository.save(config);

        auditoriaService.registrar(admin, "EDITAR_CONFIGURACION", ENTIDAD, config.getId(),
                config.getClave(), valorAnterior, valorLimpio, null);
    }

    private String nombreCompleto(Usuario u) {
        String nombre = ((u.getNombre() != null ? u.getNombre() : "") + " " + (u.getApellido() != null ? u.getApellido() : "")).trim();
        return nombre.isEmpty() ? u.getCorreo() : nombre;
    }
}
