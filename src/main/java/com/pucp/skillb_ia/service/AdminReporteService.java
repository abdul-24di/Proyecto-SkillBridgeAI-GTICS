package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoRegistro;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.util.Periodo;
import com.pucp.skillb_ia.util.ReporteExcel;
import com.pucp.skillb_ia.util.ReportePdf;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Reportes del Administrador: "Usuarios y accesos" (por mes) y "Actividad de auditoría" (por rango de fechas).
// Se arman con los usuarios y el log de auditoría que ya existen; no necesitan tablas nuevas.
@Service
public class AdminReporteService {

    public static final int MESES_HISTORIAL = 6;
    public static final int MAXIMO_USUARIOS_RANKING = 10;
    public static final int MAXIMO_DIAS_RANGO = 366;
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ---------- Usuarios y accesos ----------

    public record FilaRol(String rol, long activos, long pendientes, long inactivos) {
        public long total() { return activos + pendientes + inactivos; }
    }

    public record FilaMes(String mes, long altas, long activaciones, long logins, long loginsFallidos) {
    }

    public record ReporteUsuarios(YearMonth mes, String etiquetaMes,
                                  long altas, long activaciones, long logins, long loginsFallidos,
                                  long accesosDenegados, long recuperaciones,
                                  long preRegistrosPendientes, long preRegistrosAprobados, long preRegistrosRechazados,
                                  List<FilaRol> porRol, List<FilaMes> historial) {
    }

    // ---------- Actividad de auditoría ----------

    public record Conteo(String etiqueta, long total) {
    }

    public record ReporteAuditoria(LocalDate desde, LocalDate hasta, long total, long usuariosDistintos,
                                   long loginsFallidos, long accesosDenegados,
                                   List<Conteo> porAccion, List<Conteo> porUsuario, List<Conteo> porRol,
                                   List<Conteo> porDia) {
    }

    private final UsuarioRepository usuarioRepository;
    private final AdminUsuarioService adminUsuarioService;
    private final AdminAuditoriaService adminAuditoriaService;

    public AdminReporteService(UsuarioRepository usuarioRepository, AdminUsuarioService adminUsuarioService,
                               AdminAuditoriaService adminAuditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.adminUsuarioService = adminUsuarioService;
        this.adminAuditoriaService = adminAuditoriaService;
    }

    /** Usuarios por rol y estado (hoy), y altas, activaciones y accesos del mes y de los meses anteriores. */
    @Transactional(readOnly = true)
    public ReporteUsuarios usuarios(YearMonth mes) {
        // Estado actual de todos los usuarios, con la misma regla que el directorio de usuarios.
        Map<String, FilaRol> porRol = new LinkedHashMap<>();
        for (String rol : List.of("Administrador", "Resource Manager", "Project Manager", "Colaborador")) {
            porRol.put(rol, new FilaRol(rol, 0, 0, 0));
        }
        for (AdminUsuarioService.UsuarioFila fila : adminUsuarioService.listar()) {
            FilaRol actual = porRol.getOrDefault(fila.rolEtiqueta(), new FilaRol(fila.rolEtiqueta(), 0, 0, 0));
            porRol.put(fila.rolEtiqueta(), switch (fila.estado()) {
                case "Activo" -> new FilaRol(actual.rol(), actual.activos() + 1, actual.pendientes(), actual.inactivos());
                case "Pendiente" -> new FilaRol(actual.rol(), actual.activos(), actual.pendientes() + 1, actual.inactivos());
                default -> new FilaRol(actual.rol(), actual.activos(), actual.pendientes(), actual.inactivos() + 1);
            });
        }

        List<Usuario> usuarios = usuarioRepository.findAll();
        long preRegPendientes = contarRegistro(usuarios, EstadoRegistro.PENDIENTE);
        long preRegAprobados = contarRegistro(usuarios, EstadoRegistro.APROBADO);
        long preRegRechazados = contarRegistro(usuarios, EstadoRegistro.RECHAZADO);

        // Un solo recorrido del log para todo el historial (incluye el mes elegido).
        List<YearMonth> meses = Periodo.ultimos(mes, MESES_HISTORIAL);
        List<AdminAuditoriaService.LogFila> logs = adminAuditoriaService.listar(new AdminAuditoriaService.FiltrosAuditoria(
                null, null, null, meses.get(0).atDay(1), mes.atEndOfMonth()));

        List<FilaMes> historial = new ArrayList<>();
        for (YearMonth m : meses) {
            long altas = usuarios.stream()
                    .filter(u -> u.getFechaCreacion() != null && Periodo.contiene(m, u.getFechaCreacion().toLocalDate()))
                    .count();
            historial.add(new FilaMes(Periodo.etiqueta(m), altas, contar(logs, m, "ACTIVACION_CUENTA"),
                    contar(logs, m, "LOGIN"), contar(logs, m, "LOGIN_FALLIDO")));
        }
        FilaMes delMes = historial.get(historial.size() - 1);

        return new ReporteUsuarios(mes, Periodo.etiqueta(mes), delMes.altas(), delMes.activaciones(), delMes.logins(),
                delMes.loginsFallidos(), contar(logs, mes, "ACCESO_DENEGADO"), contar(logs, mes, "RECUPERACION_CONTRASENA"),
                preRegPendientes, preRegAprobados, preRegRechazados, new ArrayList<>(porRol.values()), historial);
    }

    /**
     * Acciones del log de auditoría en un rango de fechas, agrupadas por tipo, usuario, rol y día.
     *
     * @throws IllegalArgumentException si el rango está invertido o supera un año
     */
    @Transactional(readOnly = true)
    public ReporteAuditoria auditoria(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha \"desde\" no puede ser posterior a la fecha \"hasta\".");
        }
        if (desde.plusDays(MAXIMO_DIAS_RANGO).isBefore(hasta)) {
            throw new IllegalArgumentException("El rango no puede superar un año.");
        }
        List<AdminAuditoriaService.LogFila> logs = adminAuditoriaService.listar(
                new AdminAuditoriaService.FiltrosAuditoria(null, null, null, desde, hasta));

        List<Conteo> porDia = new ArrayList<>();
        Map<LocalDate, Long> dias = logs.stream()
                .collect(Collectors.groupingBy(AdminAuditoriaService.LogFila::fechaSolo, Collectors.counting()));
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            if (dias.containsKey(d)) {
                porDia.add(new Conteo(d.format(DIA), dias.get(d)));
            }
        }

        return new ReporteAuditoria(desde, hasta, logs.size(),
                logs.stream().map(AdminAuditoriaService.LogFila::usuarioNombre).distinct().count(),
                logs.stream().filter(l -> "LOGIN_FALLIDO".equals(l.accion())).count(),
                logs.stream().filter(l -> "ACCESO_DENEGADO".equals(l.accion())).count(),
                agrupar(logs, AdminAuditoriaService.LogFila::accionEtiqueta, Integer.MAX_VALUE),
                agrupar(logs, AdminAuditoriaService.LogFila::usuarioNombre, MAXIMO_USUARIOS_RANKING),
                agrupar(logs, AdminAuditoriaService.LogFila::rolEtiqueta, Integer.MAX_VALUE),
                porDia);
    }

    // ---------- Excel y PDF ----------
    // Las mismas hojas sirven para el Excel y para el PDF.

    private List<ReporteExcel.Hoja> hojasUsuarios(ReporteUsuarios r) {
        List<List<Object>> resumen = List.of(
                List.of("Mes", r.etiquetaMes()),
                List.of("Usuarios creados", r.altas()),
                List.of("Cuentas activadas", r.activaciones()),
                List.of("Inicios de sesión", r.logins()),
                List.of("Inicios de sesión fallidos", r.loginsFallidos()),
                List.of("Accesos denegados (403)", r.accesosDenegados()),
                List.of("Contraseñas recuperadas", r.recuperaciones()),
                List.of("Pre-registros pendientes (hoy)", r.preRegistrosPendientes()),
                List.of("Pre-registros aprobados (hoy)", r.preRegistrosAprobados()),
                List.of("Pre-registros rechazados (hoy)", r.preRegistrosRechazados()));
        List<List<Object>> roles = r.porRol().stream()
                .map(f -> List.<Object>of(f.rol(), f.activos(), f.pendientes(), f.inactivos(), f.total())).toList();
        List<List<Object>> meses = r.historial().stream()
                .map(f -> List.<Object>of(f.mes(), f.altas(), f.activaciones(), f.logins(), f.loginsFallidos())).toList();
        return List.of(
                new ReporteExcel.Hoja("Resumen", List.of("Indicador", "Valor"), resumen),
                new ReporteExcel.Hoja("Por rol", List.of("Rol", "Activos", "Pendientes", "Inactivos", "Total"), roles),
                new ReporteExcel.Hoja("Por mes", List.of("Mes", "Usuarios creados", "Cuentas activadas",
                        "Inicios de sesión", "Inicios fallidos"), meses));
    }

    private List<ReporteExcel.Hoja> hojasAuditoria(ReporteAuditoria r) {
        List<List<Object>> resumen = List.of(
                List.of("Desde", r.desde().format(DIA)),
                List.of("Hasta", r.hasta().format(DIA)),
                List.of("Acciones registradas", r.total()),
                List.of("Usuarios distintos", r.usuariosDistintos()),
                List.of("Inicios de sesión fallidos", r.loginsFallidos()),
                List.of("Accesos denegados (403)", r.accesosDenegados()));
        return List.of(
                new ReporteExcel.Hoja("Resumen", List.of("Indicador", "Valor"), resumen),
                new ReporteExcel.Hoja("Por acción", List.of("Acción", "Cantidad"), filas(r.porAccion())),
                new ReporteExcel.Hoja("Por usuario", List.of("Usuario", "Cantidad"), filas(r.porUsuario())),
                new ReporteExcel.Hoja("Por rol", List.of("Rol", "Cantidad"), filas(r.porRol())),
                new ReporteExcel.Hoja("Por día", List.of("Día", "Cantidad"), filas(r.porDia())));
    }

    public byte[] excelUsuarios(ReporteUsuarios r) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Fecha de generación", ReportePdf.ahora());
        return ReporteExcel.crear("Reporte de usuarios y accesos", datos, hojasUsuarios(r));
    }

    public byte[] excelAuditoria(ReporteAuditoria r) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Fecha de generación", ReportePdf.ahora());
        return ReporteExcel.crear("Reporte de actividad de auditoría", datos, hojasAuditoria(r));
    }

    public byte[] pdfUsuarios(ReporteUsuarios r) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Fecha de generación", ReportePdf.ahora());
        return ReportePdf.crear("Reporte de usuarios y accesos",
                "SkillBridge AI — Panel del Administrador", datos, hojasUsuarios(r));
    }

    public byte[] pdfAuditoria(ReporteAuditoria r) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Fecha de generación", ReportePdf.ahora());
        return ReportePdf.crear("Reporte de actividad de auditoría",
                "SkillBridge AI — Panel del Administrador", datos, hojasAuditoria(r));
    }

    // ---------- Utilidades ----------

    private long contarRegistro(List<Usuario> usuarios, EstadoRegistro estado) {
        return usuarios.stream().filter(u -> u.getRegistroEstado() == estado).count();
    }

    private long contar(List<AdminAuditoriaService.LogFila> logs, YearMonth mes, String accion) {
        return logs.stream().filter(l -> accion.equals(l.accion()) && Periodo.contiene(mes, l.fechaSolo())).count();
    }

    private List<Conteo> agrupar(List<AdminAuditoriaService.LogFila> logs,
                                 Function<AdminAuditoriaService.LogFila, String> clave, int limite) {
        return logs.stream()
                .collect(Collectors.groupingBy(l -> clave.apply(l) == null ? "Sin dato" : clave.apply(l), Collectors.counting()))
                .entrySet().stream()
                .map(e -> new Conteo(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(Conteo::total).reversed().thenComparing(Conteo::etiqueta))
                .limit(limite)
                .toList();
    }

    private List<List<Object>> filas(List<Conteo> conteos) {
        return conteos.stream().map(c -> List.<Object>of(c.etiqueta(), c.total())).toList();
    }
}
