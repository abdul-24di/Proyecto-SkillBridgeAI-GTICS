/*
 * Los datos de cada foro los entrega Spring Boot mediante Thymeleaf.
 * Este archivo solo controla la paginación de la grilla de tarjetas ya renderizada.
 */
document.addEventListener("DOMContentLoaded", () => {
  const comunidadRows = Array.from(document.querySelectorAll("#tab-comunidad .pm-foro-item"));
  const proyectosRows = Array.from(document.querySelectorAll("#tab-mis-proyectos .pm-foro-item"));

  if (comunidadRows.length) {
    crearPaginacionTabla({
      filas: comunidadRows,
      filtroFn: () => true,
      paginationEl: document.getElementById("forosPagination"),
      infoEl: document.getElementById("forosPaginationInfo"),
      noResultsEl: null,
      pageSize: 6,
      etiqueta: "foro(s)"
    });
  }

  if (proyectosRows.length) {
    crearPaginacionTabla({
      filas: proyectosRows,
      filtroFn: () => true,
      paginationEl: document.getElementById("forosPagination"), // Reuse the same UI or create a separate one. Actually, let's just use the same because it's a global paginator at the bottom. Wait, if it's the same, it breaks!
      infoEl: document.getElementById("forosPaginationInfo"),
      noResultsEl: null,
      pageSize: 6,
      etiqueta: "foro(s)"
    });
  }
});
