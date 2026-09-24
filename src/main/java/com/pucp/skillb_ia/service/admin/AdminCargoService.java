package com.pucp.skillb_ia.service.admin;

import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

// Épica 5 — Catálogo de cargos y su matriz salarial (Junior / Semi-Senior / Senior).
// El sueldo_base de cada colaborador se deriva de su cargo + nivel de experiencia,
// así que al cambiar una tarifa se recalculan los sueldos de quienes tienen ese cargo.
@Service
public class AdminCargoService {

    private final CargoRepository cargoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public AdminCargoService(CargoRepository cargoRepository, UsuarioRepository usuarioRepository,
                             AuditoriaService auditoriaService) {
        this.cargoRepository = cargoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<Cargo> listarTodosLosCargos() {
        return cargoRepository.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public List<Cargo> listarCargosActivos() {
        return cargoRepository.findByActivoTrueOrderByNombreAsc();
    }

    @Transactional
    public void crear(String nombre, BigDecimal sueldoJunior, BigDecimal sueldoSemiSenior,
                      BigDecimal sueldoSenior, Usuario admin) {
        String nombreLimpio = nombre == null ? "" : nombre.strip();
        if (nombreLimpio.isEmpty()) {
            throw new IllegalArgumentException("El nombre del cargo es obligatorio.");
        }
        if (nombreLimpio.length() > 100) {
            throw new IllegalArgumentException("El nombre del cargo no puede superar los 100 caracteres.");
        }
        if (cargoRepository.existsByNombreIgnoreCase(nombreLimpio)) {
            throw new IllegalArgumentException("Ya existe un cargo llamado \"" + nombreLimpio + "\".");
        }
        validarTarifas(sueldoJunior, sueldoSemiSenior, sueldoSenior);

        Cargo cargo = cargoRepository.save(new Cargo(nombreLimpio, sueldoJunior, sueldoSemiSenior, sueldoSenior));
        auditoriaService.registrar(admin, "CREAR_CARGO", "CARGO", cargo.getId(),
                "Creó el cargo \"" + nombreLimpio + "\".", null, resumenTarifas(cargo), null);
    }

    // Devuelve cuántos colaboradores tuvieron su sueldo recalculado.
    @Transactional
    public int editarTarifas(Long cargoId, BigDecimal sueldoJunior, BigDecimal sueldoSemiSenior,
                             BigDecimal sueldoSenior, Usuario admin) {
        Cargo cargo = obtener(cargoId);
        validarTarifas(sueldoJunior, sueldoSemiSenior, sueldoSenior);

        String anterior = resumenTarifas(cargo);
        cargo.setSueldoJunior(sueldoJunior);
        cargo.setSueldoSemiSenior(sueldoSemiSenior);
        cargo.setSueldoSenior(sueldoSenior);
        cargoRepository.save(cargo);

        int actualizados = recalcularSueldos(cargo);
        auditoriaService.registrar(admin, "EDITAR_CARGO", "CARGO", cargo.getId(),
                "Actualizó las tarifas del cargo \"" + cargo.getNombre() + "\" ("
                        + actualizados + " sueldo(s) recalculado(s)).",
                anterior, resumenTarifas(cargo), null);
        return actualizados;
    }

    // Desactivar solo lo oculta al asignar cargos nuevos; quienes ya lo tienen lo conservan.
    @Transactional
    public boolean alternarEstado(Long cargoId, Usuario admin) {
        Cargo cargo = obtener(cargoId);
        cargo.setActivo(!cargo.isActivo());
        cargoRepository.save(cargo);
        auditoriaService.registrar(admin, cargo.isActivo() ? "REACTIVAR_CARGO" : "DESACTIVAR_CARGO",
                "CARGO", cargo.getId(),
                (cargo.isActivo() ? "Reactivó" : "Desactivó") + " el cargo \"" + cargo.getNombre() + "\".");
        return cargo.isActivo();
    }

    // Asigna el sueldo que le toca al colaborador según su cargo y nivel actuales.
    // Si falta el cargo, el nivel o la tarifa, deja el sueldo como está.
    public static boolean aplicarSueldoSegunCargo(Usuario colaborador) {
        if (colaborador.getCargo() == null) return false;
        BigDecimal nuevo = colaborador.getCargo().sueldoPara(colaborador.getNivelExperiencia());
        if (nuevo == null) return false;
        if (colaborador.getSueldoBase() != null && colaborador.getSueldoBase().compareTo(nuevo) == 0) return false;
        colaborador.setSueldoBase(nuevo);
        return true;
    }

    private int recalcularSueldos(Cargo cargo) {
        int actualizados = 0;
        for (Usuario colaborador : usuarioRepository.findByCargo(cargo)) {
            if (aplicarSueldoSegunCargo(colaborador)) {
                usuarioRepository.save(colaborador);
                actualizados++;
            }
        }
        return actualizados;
    }

    private Cargo obtener(Long cargoId) {
        if (cargoId == null) throw new IllegalArgumentException("Cargo no encontrado.");
        return cargoRepository.findById(cargoId)
                .orElseThrow(() -> new IllegalArgumentException("Cargo no encontrado."));
    }

    private static void validarTarifas(BigDecimal junior, BigDecimal semiSenior, BigDecimal senior) {
        if (junior == null || semiSenior == null || senior == null) {
            throw new IllegalArgumentException("Debes ingresar las tres tarifas del cargo.");
        }
        if (junior.signum() <= 0 || semiSenior.signum() <= 0 || senior.signum() <= 0) {
            throw new IllegalArgumentException("Las tarifas deben ser mayores a 0.");
        }
        if (junior.compareTo(semiSenior) > 0 || semiSenior.compareTo(senior) > 0) {
            throw new IllegalArgumentException("Las tarifas deben ir en orden: Junior ≤ Semi-Senior ≤ Senior.");
        }
    }

    private static String resumenTarifas(Cargo cargo) {
        return "Junior " + cargo.getSueldoJunior() + " / Semi-Senior " + cargo.getSueldoSemiSenior()
                + " / Senior " + cargo.getSueldoSenior();
    }
}
