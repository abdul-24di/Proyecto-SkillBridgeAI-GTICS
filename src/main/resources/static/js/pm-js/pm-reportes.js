/*
 * Los datos de cada proyecto los entrega Spring Boot mediante Thymeleaf.
 * Este archivo solo controla la paginación de la grilla de tarjetas ya renderizada.
 */
document.addEventListener("DOMContentLoaded", () => {
  const rows = Array.from(document.querySelectorAll(".pm-report-item"));

  if (!rows.length) return;

  crearPaginacionTabla({
    filas: rows,
    filtroFn: () => true,
    paginationEl: document.getElementById("reportesPagination"),
    infoEl: document.getElementById("reportesPaginationInfo"),
    noResultsEl: null,
    pageSize: 6,
    etiqueta: "proyecto(s)"
  });
});
