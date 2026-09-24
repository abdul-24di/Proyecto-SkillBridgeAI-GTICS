/*
 * Los datos de cada actividad los entrega Spring Boot mediante Thymeleaf.
 * Este archivo solo controla la paginación de la tabla ya renderizada.
 */
document.addEventListener("DOMContentLoaded", () => {
  const rows = Array.from(document.querySelectorAll(".pm-activity-row"));

  if (!rows.length) return;

  crearPaginacionTabla({
    filas: rows,
    filtroFn: () => true,
    paginationEl: document.getElementById("actividadesPagination"),
    infoEl: document.getElementById("actividadesPaginationInfo"),
    noResultsEl: null,
    pageSize: 10,
    etiqueta: "actividad(es)"
  });
});
