/* Filtro + paginación cliente para el catalogo de habilidades (busqueda + categoria + estado). */
document.addEventListener("DOMContentLoaded", function () {
  const searchInput = document.getElementById("skillSearch");
  const categoryFilter = document.getElementById("skillCategoryFilter");
  const statusFilter = document.getElementById("skillStatusFilter");
  const clearBtn = document.getElementById("clearSkillFiltersBtn");
  const rows = Array.from(document.querySelectorAll("#skillsTableBody tr[data-search]"));

  if (rows.length) {
    function coincide(row) {
      const texto = (searchInput?.value || "").toLowerCase().trim();
      const categoria = categoryFilter?.value || "all";
      const estado = statusFilter?.value || "all";

      const coincideTexto = !texto || (row.dataset.search || "").indexOf(texto) !== -1;
      const coincideCategoria = categoria === "all" || row.dataset.category === categoria;
      const coincideEstado = estado === "all" || row.dataset.status === estado;
      return coincideTexto && coincideCategoria && coincideEstado;
    }

    const paginacion = crearPaginacionTabla({
      filas: rows,
      filtroFn: coincide,
      paginationEl: document.getElementById("skillPagination"),
      infoEl: document.getElementById("skillPaginationInfo"),
      noResultsEl: document.getElementById("skillNoResults"),
      pageSize: 10,
      etiqueta: "habilidad(es)"
    });

    searchInput?.addEventListener("keyup", paginacion.reset);
    categoryFilter?.addEventListener("change", paginacion.reset);
    statusFilter?.addEventListener("change", paginacion.reset);
    clearBtn?.addEventListener("click", function () {
      if (searchInput) searchInput.value = "";
      if (categoryFilter) categoryFilter.value = "all";
      if (statusFilter) statusFilter.value = "all";
      paginacion.reset();
    });
  }

  const categoryRows = Array.from(document.querySelectorAll("#categoriesTableBody tr[data-category-row]"));
  if (categoryRows.length) {
    crearPaginacionTabla({
      filas: categoryRows,
      filtroFn: () => true,
      paginationEl: document.getElementById("categoryPagination"),
      infoEl: document.getElementById("categoryPaginationInfo"),
      pageSize: 10,
      etiqueta: "categoría(s)"
    });
  }
});
