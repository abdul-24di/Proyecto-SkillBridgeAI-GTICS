/* Filtros y paginación de la consulta de foros del Resource Manager. */
const pageSize = 4;
let currentPage = 1;

const forumSearch = document.getElementById("forumSearch");
const forumStatusFilter = document.getElementById("forumStatusFilter");
const forumActivityFilter = document.getElementById("forumActivityFilter");
const clearForumFilters = document.getElementById("clearForumFilters");
const forumRows = Array.from(document.querySelectorAll(".forum-data-row"));
const forumPagination = document.getElementById("forumPagination");
const forumPaginationInfo = document.getElementById("forumPaginationInfo");
const forumEmptyFiltered = document.getElementById("forumEmptyFiltered");

function normalize(value) {
  return (value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
}

function filteredRows() {
  const search = normalize(forumSearch.value.trim());
  const status = forumStatusFilter.value;
  const activity = forumActivityFilter.value;

  return forumRows.filter(row => {
    const matchesSearch = normalize(row.dataset.search).includes(search);
    const matchesStatus = status === "all" || row.dataset.status === status;
    const recent = row.dataset.recent === "true";
    const matchesActivity = activity === "all"
      || (activity === "recent" && recent)
      || (activity === "older" && !recent);
    return matchesSearch && matchesStatus && matchesActivity;
  });
}

function renderForums() {
  const filtered = filteredRows();
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  if (currentPage > totalPages) currentPage = totalPages;

  forumRows.forEach(row => row.classList.add("d-none"));
  const start = (currentPage - 1) * pageSize;
  filtered.slice(start, start + pageSize).forEach(row => row.classList.remove("d-none"));

  if (forumEmptyFiltered) {
    forumEmptyFiltered.classList.toggle("d-none", filtered.length !== 0 || forumRows.length === 0);
  }

  const first = filtered.length ? start + 1 : 0;
  const last = Math.min(start + pageSize, filtered.length);
  forumPaginationInfo.textContent = `Mostrando ${first}-${last} de ${filtered.length} foros`;
  renderPagination(totalPages, filtered.length);
}

function renderPagination(totalPages, totalItems) {
  forumPagination.innerHTML = "";
  if (!totalItems) return;

  forumPagination.appendChild(pageButton("Anterior", currentPage === 1, () => {
    currentPage--;
    renderForums();
  }));

  for (let page = 1; page <= totalPages; page++) {
    const item = pageButton(String(page), false, () => {
      currentPage = page;
      renderForums();
    });
    if (page === currentPage) item.classList.add("active");
    forumPagination.appendChild(item);
  }

  forumPagination.appendChild(pageButton("Siguiente", currentPage === totalPages, () => {
    currentPage++;
    renderForums();
  }));
}

function pageButton(label, disabled, action) {
  const item = document.createElement("li");
  item.className = `page-item ${disabled ? "disabled" : ""}`;
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

[forumSearch, forumStatusFilter, forumActivityFilter].forEach(element => {
  const eventName = element.tagName === "INPUT" ? "input" : "change";
  element.addEventListener(eventName, () => {
    currentPage = 1;
    renderForums();
  });
});

clearForumFilters.addEventListener("click", () => {
  forumSearch.value = "";
  forumStatusFilter.value = "all";
  forumActivityFilter.value = "all";
  currentPage = 1;
  renderForums();
});

renderForums();
