/*
 * Los datos de cada tabla los entrega Spring Boot mediante Thymeleaf. Este
 * archivo solo controla la paginación de las 4 tablas ya renderizadas en la
 * vista de asignaciones del proyecto (activas, propuestas pendientes,
 * trámite y historial), cada una con su propio contador independiente.
 */
document.addEventListener("DOMContentLoaded", () => {
  const activasRows = Array.from(document.querySelectorAll(".pm-asig-activa-row"));
  if (activasRows.length) {
    crearPaginacionTabla({
      filas: activasRows,
      filtroFn: () => true,
      paginationEl: document.getElementById("activasPagination"),
      infoEl: document.getElementById("activasPaginationInfo"),
      noResultsEl: null,
      pageSize: 10,
      etiqueta: "colaborador(es)"
    });
  }

  const pendientesRows = Array.from(document.querySelectorAll(".pm-asig-pendiente-row"));
  if (pendientesRows.length) {
    crearPaginacionTabla({
      filas: pendientesRows,
      filtroFn: () => true,
      paginationEl: document.getElementById("pendientesPmPagination"),
      infoEl: document.getElementById("pendientesPmPaginationInfo"),
      noResultsEl: null,
      pageSize: 10,
      etiqueta: "propuesta(s)"
    });
  }

  const tramiteRows = Array.from(document.querySelectorAll(".pm-asig-tramite-row"));
  if (tramiteRows.length) {
    crearPaginacionTabla({
      filas: tramiteRows,
      filtroFn: () => true,
      paginationEl: document.getElementById("tramitePagination"),
      infoEl: document.getElementById("tramitePaginationInfo"),
      noResultsEl: null,
      pageSize: 10,
      etiqueta: "asignación(es)"
    });
  }

  const historialRows = Array.from(document.querySelectorAll(".pm-asig-historial-row"));
  if (historialRows.length) {
    crearPaginacionTabla({
      filas: historialRows,
      filtroFn: () => true,
      paginationEl: document.getElementById("historialPagination"),
      infoEl: document.getElementById("historialPaginationInfo"),
      noResultsEl: null,
      pageSize: 10,
      etiqueta: "registro(s)"
    });
  }
});
