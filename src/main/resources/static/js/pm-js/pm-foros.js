/*
 * Los datos de cada foro los entrega Spring Boot mediante Thymeleaf.
 * Este archivo solo controla la paginación de la grilla de tarjetas ya renderizada.
 */
document.addEventListener("DOMContentLoaded", () => {
  const rows = Array.from(document.querySelectorAll(".pm-foro-item"));

  if (!rows.length) return;

  crearPaginacionTabla({
    filas: rows,
    filtroFn: () => true,
    paginationEl: document.getElementById("forosPagination"),
    infoEl: document.getElementById("forosPaginationInfo"),
    noResultsEl: null,
    pageSize: 6,
    etiqueta: "foro(s)"
  });
});
