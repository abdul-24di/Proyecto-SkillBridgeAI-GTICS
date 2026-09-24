/* Filtro + paginación cliente para el directorio de usuarios (busqueda + rol + estado). */
document.addEventListener("DOMContentLoaded", function () {
  const searchInput = document.getElementById("userSearch");
  const roleFilter = document.getElementById("userRoleFilter");
  const statusFilter = document.getElementById("userStatusFilter");
  const clearBtn = document.getElementById("clearFiltersBtn");
  const rows = Array.from(document.querySelectorAll("tbody tr[data-search]"));

  if (!rows.length) return;

  function coincide(row) {
    const texto = (searchInput?.value || "").toLowerCase().trim();
    const rol = roleFilter?.value || "all";
    const estado = statusFilter?.value || "all";

    const coincideTexto = !texto || (row.dataset.search || "").indexOf(texto) !== -1;
    const coincideRol = rol === "all" || row.dataset.role === rol;
    const coincideEstado = estado === "all" || row.dataset.status === estado;
    return coincideTexto && coincideRol && coincideEstado;
  }

  const paginacion = crearPaginacionTabla({
    filas: rows,
    filtroFn: coincide,
    paginationEl: document.getElementById("userPagination"),
    infoEl: document.getElementById("userPaginationInfo"),
    noResultsEl: document.getElementById("userNoResults"),
    pageSize: 10,
    etiqueta: "usuario(s)"
  });

  searchInput?.addEventListener("keyup", paginacion.reset);
  roleFilter?.addEventListener("change", paginacion.reset);
  statusFilter?.addEventListener("change", paginacion.reset);
  clearBtn?.addEventListener("click", function () {
    if (searchInput) searchInput.value = "";
    if (roleFilter) roleFilter.value = "all";
    if (statusFilter) statusFilter.value = "all";
    paginacion.reset();
  });
});
