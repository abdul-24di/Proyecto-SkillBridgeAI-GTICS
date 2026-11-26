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

/**
 * Épica 5 — Catálogo de cargos y su matriz salarial (Junior / Semi-Senior / Senior). El sueldo_base de
 * cada colaborador se deriva de su cargo + nivel de experiencia, así que al cambiar una tarifa se recalculan
 * los sueldos de quienes tienen ese cargo.
 */
@Service
public class AdminCargoService {

    private final CargoRepository cargoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public AdminCargoService(CargoRepository cargoRepository, UsuarioRepository usuarioRepository,
                             AuditoriaService auditoriaService) {
        this.cargoRepository = cargoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lista todos los cargos por nombre.
     *
     * @return cargos
     */
    @Transactional(readOnly = true)
    public List<Cargo> listarTodosLosCargos() {
        return cargoRepository.findAllByOrderByNombreAsc();
    }

    /**
     * Lista los cargos que se pueden asignar.
     *
     * @return cargos activos
     */
    @Transactional(readOnly = true)
    public List<Cargo> listarCargosActivos() {
        return cargoRepository.findByActivoTrueOrderByNombreAsc();
    }

    /**
     * Crea un cargo con sus tarifas y lo registra en la auditoría.
     *
     * @param nombre nombre ingresado
     * @param sueldoJunior tarifa mensual del nivel Junior
     * @param sueldoSemiSenior tarifa mensual del nivel Semi-Senior
     * @param sueldoSenior tarifa mensual del nivel Senior
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si el nombre o las tarifas no son válidos, o el nombre ya existe
     */
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

    /**
     * Cambia las tarifas de un cargo y recalcula el sueldo base de los colaboradores que lo tienen.
     * <p>
     * Devuelve cuántos colaboradores tuvieron su sueldo recalculado.
     *
     * @param cargoId id del cargo
     * @param sueldoJunior tarifa mensual del nivel Junior
     * @param sueldoSemiSenior tarifa mensual del nivel Semi-Senior
     * @param sueldoSenior tarifa mensual del nivel Senior
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @return colaboradores cuyo sueldo cambió
     * @throws IllegalArgumentException si el cargo no existe o las tarifas no son válidas
     */
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

    /**
     * Activa el cargo si estaba inactivo, o lo desactiva.
     * <p>
     * Desactivar solo lo oculta al asignar cargos nuevos; quienes ya lo tienen lo conservan.
     *
     * @param cargoId id del cargo
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @return {@code true} si quedó activo
     * @throws IllegalArgumentException si no existe
     */
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

    /**
     * Aplica al colaborador la tarifa de su cargo y nivel.
     * <p>
     * Asigna el sueldo que le toca al colaborador según su cargo y nivel actuales. Si falta el cargo, el nivel o
     * la tarifa, deja el sueldo como está.
     *
     * @param colaborador colaborador al que se le aplica la tarifa
     * @return {@code true} si el sueldo cambió
     */
    public static boolean aplicarSueldoSegunCargo(Usuario colaborador) {
        if (colaborador.getCargo() == null) return false;
        BigDecimal nuevo = colaborador.getCargo().sueldoPara(colaborador.getNivelExperiencia());
        if (nuevo == null) return false;
        if (colaborador.getSueldoBase() != null && colaborador.getSueldoBase().compareTo(nuevo) == 0) return false;
        colaborador.setSueldoBase(nuevo);
        return true;
    }

    /**
     * Recalcula el sueldo de los colaboradores de un cargo.
     *
     * @param cargo cargo
     * @return colaboradores cuyo sueldo cambió
     */
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

    /**
     * Busca un cargo por su id.
     *
     * @param cargoId id del cargo
     * @return el cargo
     * @throws IllegalArgumentException si no existe
     */
    private Cargo obtener(Long cargoId) {
        if (cargoId == null) throw new IllegalArgumentException("Cargo no encontrado.");
        return cargoRepository.findById(cargoId)
                .orElseThrow(() -> new IllegalArgumentException("Cargo no encontrado."));
    }

    /**
     * Comprueba que estén las tres tarifas, sean mayores que cero y vayan de menor a mayor.
     *
     * @param junior tarifa Junior
     * @param semiSenior tarifa Semi-Senior
     * @param senior tarifa Senior
     * @throws IllegalArgumentException si no cumplen
     */
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

    /**
     * Arma el texto de las tarifas para la auditoría.
     *
     * @param cargo cargo
     * @return tarifas en una línea
     */
    private static String resumenTarifas(Cargo cargo) {
        return "Junior " + cargo.getSueldoJunior() + " / Semi-Senior " + cargo.getSueldoSemiSenior()
                + " / Senior " + cargo.getSueldoSenior();
    }
}
