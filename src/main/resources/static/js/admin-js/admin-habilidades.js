/* Filtro cliente para el catalogo de habilidades (busqueda + categoria + estado). */
document.addEventListener("DOMContentLoaded", function () {
  const searchInput = document.getElementById("skillSearch");
  const categoryFilter = document.getElementById("skillCategoryFilter");
  const statusFilter = document.getElementById("skillStatusFilter");
  const clearBtn = document.getElementById("clearSkillFiltersBtn");
  const rows = document.querySelectorAll("tbody tr[data-search]");

  if (!rows.length) return;

  function aplicarFiltros() {
    const texto = (searchInput?.value || "").toLowerCase().trim();
    const categoria = categoryFilter?.value || "all";
    const estado = statusFilter?.value || "all";

    rows.forEach(row => {
      const coincideTexto = !texto || (row.dataset.search || "").indexOf(texto) !== -1;
      const coincideCategoria = categoria === "all" || row.dataset.category === categoria;
      const coincideEstado = estado === "all" || row.dataset.status === estado;
      row.style.display = (coincideTexto && coincideCategoria && coincideEstado) ? "" : "none";
    });
  }

  searchInput?.addEventListener("keyup", aplicarFiltros);
  categoryFilter?.addEventListener("change", aplicarFiltros);
  statusFilter?.addEventListener("change", aplicarFiltros);
  clearBtn?.addEventListener("click", function () {
    if (searchInput) searchInput.value = "";
    if (categoryFilter) categoryFilter.value = "all";
    if (statusFilter) statusFilter.value = "all";
    aplicarFiltros();
  });
});
