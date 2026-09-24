/*
 * Los datos de cada colaborador los entrega Spring Boot mediante Thymeleaf.
 * Este archivo solo controla la paginación de la tabla ya renderizada.
 */
document.addEventListener("DOMContentLoaded", () => {
  const rows = Array.from(document.querySelectorAll(".pm-reporte-colab-row"));

  if (!rows.length) return;

  crearPaginacionTabla({
    filas: rows,
    filtroFn: () => true,
    paginationEl: document.getElementById("colaboradoresPagination"),
    infoEl: document.getElementById("colaboradoresPaginationInfo"),
    noResultsEl: null,
    pageSize: 10,
    etiqueta: "colaborador(es)"
  });
});
