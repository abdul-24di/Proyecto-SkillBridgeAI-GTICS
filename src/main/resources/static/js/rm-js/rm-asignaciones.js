/* Filtros de la bandeja. Los datos se renderizan desde MySQL con Thymeleaf. */
document.addEventListener("DOMContentLoaded", () => {
  const pageSize = 10;
  let currentPage = 1;

  const rows = [...document.querySelectorAll(".assignment-row")];
  const tabs = [...document.querySelectorAll(".assignment-tabs button")];
  const search = document.getElementById("searchInput");
  const origin = document.getElementById("originFilter");
  const status = document.getElementById("statusFilter");
  const clear = document.getElementById("clearFilters");
  const empty = document.getElementById("emptyAssignments");
  const pagination = document.getElementById("pagination");
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

  function getFilteredItems() {
    const term = search.value.trim().toLowerCase();
    return rows.filter(row => row.dataset.group === currentTab
      && (origin.value === "all" || row.dataset.origin === origin.value)
      && (status.value === "all" || row.dataset.status === status.value)
      && row.dataset.search.toLowerCase().includes(term));
  }

  function render() {
    const filteredItems = getFilteredItems();
    const totalPages = Math.max(1, Math.ceil(filteredItems.length / pageSize));
    currentPage = Math.min(currentPage, totalPages);

    rows.forEach(row => row.classList.add("d-none"));

    const start = (currentPage - 1) * pageSize;
    filteredItems.slice(start, start + pageSize)
      .forEach(row => row.classList.remove("d-none"));

    empty.classList.toggle("d-none", filteredItems.length !== 0);

    const firstItem = filteredItems.length ? start + 1 : 0;
    const lastItem = Math.min(start + pageSize, filteredItems.length);
    info.textContent = `Mostrando ${firstItem}-${lastItem} de ${filteredItems.length} registro${filteredItems.length === 1 ? "" : "s"}`;

    renderPagination(totalPages, filteredItems.length);
  }

  function renderPagination(totalPages, resultCount) {
    pagination.innerHTML = "";
    if (resultCount === 0) return;

    pagination.appendChild(createPageItem("Anterior", currentPage === 1, false, () => {
      currentPage--;
      render();
    }));

    for (let page = 1; page <= totalPages; page++) {
      pagination.appendChild(createPageItem(String(page), false, page === currentPage, () => {
        currentPage = page;
        render();
      }));
    }

    pagination.appendChild(createPageItem("Siguiente", currentPage === totalPages, false, () => {
      currentPage++;
      render();
    }));
  }

  function createPageItem(label, disabled, active, action) {
    const item = document.createElement("li");
    item.className = `page-item${disabled ? " disabled" : ""}${active ? " active" : ""}`;

    const link = document.createElement("a");
    link.className = "page-link";
    link.href = "#";
    link.textContent = label;
    link.addEventListener("click", event => {
      event.preventDefault();
      if (!disabled) action();
    });

    item.appendChild(link);
    return item;
  }

  tabs.forEach(tab => tab.addEventListener("click", () => {
    tabs.forEach(item => item.classList.remove("active"));
    tab.classList.add("active");
    currentTab = tab.dataset.tab;
    updateStatusOptions();
    currentPage = 1;
    render();
  }));
  search.addEventListener("input", () => {
    currentPage = 1;
    render();
  });
  origin.addEventListener("change", () => {
    currentPage = 1;
    render();
  });
  status.addEventListener("change", () => {
    currentPage = 1;
    render();
  });
  clear.addEventListener("click", () => {
    search.value = "";
    origin.value = "all";
    updateStatusOptions();
    currentPage = 1;
    render();
  });
  updateStatusOptions();
  render();
});
