/* Filtro cliente para el directorio de usuarios (busqueda + rol + estado). */
document.addEventListener("DOMContentLoaded", function () {
  const searchInput = document.getElementById("userSearch");
  const roleFilter = document.getElementById("userRoleFilter");
  const statusFilter = document.getElementById("userStatusFilter");
  const clearBtn = document.getElementById("clearFiltersBtn");
  const rows = document.querySelectorAll("tbody tr[data-search]");

  if (!rows.length) return;

  function aplicarFiltros() {
    const texto = (searchInput?.value || "").toLowerCase().trim();
    const rol = roleFilter?.value || "all";
    const estado = statusFilter?.value || "all";

    rows.forEach(row => {
      const coincideTexto = !texto || (row.dataset.search || "").indexOf(texto) !== -1;
      const coincideRol = rol === "all" || row.dataset.role === rol;
      const coincideEstado = estado === "all" || row.dataset.status === estado;
      row.style.display = (coincideTexto && coincideRol && coincideEstado) ? "" : "none";
    });
  }

  searchInput?.addEventListener("keyup", aplicarFiltros);
  roleFilter?.addEventListener("change", aplicarFiltros);
  statusFilter?.addEventListener("change", aplicarFiltros);
  clearBtn?.addEventListener("click", function () {
    if (searchInput) searchInput.value = "";
    if (roleFilter) roleFilter.value = "all";
    if (statusFilter) statusFilter.value = "all";
    aplicarFiltros();
  });
});
