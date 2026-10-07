/* Paginación cliente para las tablas de cursos vigentes/finalizados (sin filtros propios). */
document.addEventListener("DOMContentLoaded", function () {
  [
    { filas: ".curso-vigente-row", base: "cursosVigentes", etiqueta: "curso(s) en curso" },
    { filas: ".curso-finalizado-row", base: "cursosFinalizados", etiqueta: "curso(s) caducado(s)" }
  ].forEach(function (cfg) {
    const filas = Array.from(document.querySelectorAll(cfg.filas));
    if (!filas.length) return;
    crearPaginacionTabla({
      filas: filas,
      filtroFn: () => true,
      paginationEl: document.getElementById(cfg.base + "Pagination"),
      infoEl: document.getElementById(cfg.base + "PaginationInfo"),
      pageSize: 10,
      etiqueta: cfg.etiqueta
    });
  });
});
