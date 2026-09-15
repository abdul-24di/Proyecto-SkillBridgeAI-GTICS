/* Filtros de la bandeja. Los datos se renderizan desde MySQL con Thymeleaf. */
document.addEventListener("DOMContentLoaded", () => {
  const rows = [...document.querySelectorAll(".assignment-row")];
  const tabs = [...document.querySelectorAll(".assignment-tabs button")];
  const search = document.getElementById("searchInput");
  const origin = document.getElementById("originFilter");
  const status = document.getElementById("statusFilter");
  const clear = document.getElementById("clearFilters");
  const empty = document.getElementById("emptyAssignments");
  const info = document.getElementById("paginationInfo");
  let currentTab = "pending";

  const statusOptions = {
    pending: ["Pendiente RM", "Pendiente PM", "Pendiente"],
    active: ["Activa"],
    history: ["Finalizada", "Rechazada"]
  };

  function updateStatusOptions() {
    status.innerHTML = '<option value="all">Todos</option>'
      + statusOptions[currentTab].map(value => `<option value="${value}">${value}</option>`).join("");
  }

  function render() {
    const term = search.value.trim().toLowerCase();
    let visible = 0;
    rows.forEach(row => {
      const show = row.dataset.group === currentTab
        && (origin.value === "all" || row.dataset.origin === origin.value)
        && (status.value === "all" || row.dataset.status === status.value)
        && row.dataset.search.toLowerCase().includes(term);
      row.classList.toggle("d-none", !show);
      if (show) visible++;
    });
    empty.classList.toggle("d-none", visible !== 0);
    info.textContent = `${visible} registro${visible === 1 ? "" : "s"}`;
  }

  tabs.forEach(tab => tab.addEventListener("click", () => {
    tabs.forEach(item => item.classList.remove("active"));
    tab.classList.add("active");
    currentTab = tab.dataset.tab;
    updateStatusOptions();
    render();
  }));
  search.addEventListener("input", render);
  origin.addEventListener("change", render);
  status.addEventListener("change", render);
  clear.addEventListener("click", () => {
    search.value = "";
    origin.value = "all";
    updateStatusOptions();
    render();
  });
  updateStatusOptions();
  render();
});
