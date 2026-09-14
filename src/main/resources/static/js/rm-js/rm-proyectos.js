/* Los proyectos los entrega Spring Boot. Aquí solo se filtra y pagina el DOM renderizado. */
document.addEventListener("DOMContentLoaded", () => {
  const pageSize = 6;
  let currentPage = 1;
  const searchInput = document.getElementById("searchInput");
  const statusFilter = document.getElementById("statusFilter");
  const priorityFilter = document.getElementById("priorityFilter");
  const vacancyFilter = document.getElementById("vacancyFilter");
  const clearFilters = document.getElementById("clearFilters");
  const pagination = document.getElementById("pagination");
  const paginationInfo = document.getElementById("paginationInfo");
  const noResults = document.getElementById("noProjectResults");
  const items = Array.from(document.querySelectorAll(".project-item"));

  if (!searchInput || !statusFilter || !priorityFilter || !vacancyFilter) return;

  function filteredItems() {
    const search = searchInput.value.trim().toLocaleLowerCase("es");
    return items.filter(item => {
      const vacancies = Number(item.dataset.vacancies || 0);
      return (item.dataset.search || "").toLocaleLowerCase("es").includes(search)
        && (statusFilter.value === "all" || item.dataset.status === statusFilter.value)
        && (priorityFilter.value === "all" || item.dataset.priority === priorityFilter.value)
        && (vacancyFilter.value === "all"
          || (vacancyFilter.value === "with" && vacancies > 0)
          || (vacancyFilter.value === "full" && vacancies === 0));
    });
  }

  function render() {
    const filtered = filteredItems();
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    currentPage = Math.min(currentPage, totalPages);
    items.forEach(item => item.classList.add("d-none"));
    const start = (currentPage - 1) * pageSize;
    filtered.slice(start, start + pageSize).forEach(item => item.classList.remove("d-none"));
    noResults?.classList.toggle("d-none", filtered.length !== 0 || items.length === 0);
    const first = filtered.length ? start + 1 : 0;
    paginationInfo.textContent = `Mostrando ${first}-${Math.min(start + pageSize, filtered.length)} de ${filtered.length} proyectos`;
    renderPages(totalPages, filtered.length);
  }

  function renderPages(totalPages, count) {
    pagination.innerHTML = "";
    if (!count) return;
    pagination.appendChild(pageItem("Anterior", currentPage === 1, false, () => { currentPage--; render(); }));
    for (let page = 1; page <= totalPages; page++) {
      pagination.appendChild(pageItem(String(page), false, page === currentPage, () => { currentPage = page; render(); }));
    }
    pagination.appendChild(pageItem("Siguiente", currentPage === totalPages, false, () => { currentPage++; render(); }));
  }

  function pageItem(label, disabled, active, action) {
    const item = document.createElement("li");
    item.className = `page-item${disabled ? " disabled" : ""}${active ? " active" : ""}`;
    const link = document.createElement("a");
    link.className = "page-link";
    link.href = "#";
    link.textContent = label;
    link.addEventListener("click", event => { event.preventDefault(); if (!disabled) action(); });
    item.appendChild(link);
    return item;
  }

  [searchInput, statusFilter, priorityFilter, vacancyFilter].forEach(element =>
    element.addEventListener(element.tagName === "INPUT" ? "input" : "change", () => { currentPage = 1; render(); })
  );
  clearFilters.addEventListener("click", () => {
    searchInput.value = "";
    statusFilter.value = "all";
    priorityFilter.value = "all";
    vacancyFilter.value = "all";
    currentPage = 1;
    render();
  });
  render();
});
