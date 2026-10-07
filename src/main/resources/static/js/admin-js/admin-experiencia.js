/* Paginación cliente para las tablas de CVs pendientes/revisados (sin filtros propios). */
document.addEventListener("DOMContentLoaded", function () {
  const filasPendientes = Array.from(document.querySelectorAll("#tab-pendientes .cv-pendiente-row"));
  if (filasPendientes.length) {
    crearPaginacionTabla({
      filas: filasPendientes,
      filtroFn: () => true,
      paginationEl: document.getElementById("cvPendientesPagination"),
      infoEl: document.getElementById("cvPendientesPaginationInfo"),
      noResultsEl: document.getElementById("cvPendientesNoResults"),
      pageSize: 10,
      etiqueta: "CV(s) pendiente(s)"
    });
  }

  const filasRevisados = Array.from(document.querySelectorAll("#tab-revisados .cv-revisado-row"));
  if (filasRevisados.length) {
    crearPaginacionTabla({
      filas: filasRevisados,
      filtroFn: () => true,
      paginationEl: document.getElementById("cvRevisadosPagination"),
      infoEl: document.getElementById("cvRevisadosPaginationInfo"),
      noResultsEl: document.getElementById("cvRevisadosNoResults"),
      pageSize: 10,
      etiqueta: "CV(s) revisado(s)"
    });
  }
});

document.addEventListener("DOMContentLoaded", function () {
  const filasRechazados = Array.from(document.querySelectorAll("#tab-rechazados .cv-rechazado-row"));
  if (filasRechazados.length) {
    crearPaginacionTabla({
      filas: filasRechazados,
      filtroFn: () => true,
      paginationEl: document.getElementById("cvRechazadosPagination"),
      infoEl: document.getElementById("cvRechazadosPaginationInfo"),
      noResultsEl: document.getElementById("cvRechazadosNoResults"),
      pageSize: 10,
      etiqueta: "registro(s) rechazado(s)"
    });
  }
});
