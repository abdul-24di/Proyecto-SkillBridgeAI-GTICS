const rows = Array.from(document.querySelectorAll(".request-row"));
const searchInput = document.getElementById("searchInput");
const statusFilter = document.getElementById("statusFilter");
const priorityFilter = document.getElementById("priorityFilter");
const clearFilters = document.getElementById("clearFilters");
const empty = document.getElementById("emptyRequests");
const pagination = document.getElementById("pagination");
const paginationInfo = document.getElementById("paginationInfo");
const pageSize = 6;
let currentPage = 1;

function filteredRows() {
  const query = searchInput.value.trim().toLowerCase();
  return rows.filter(row => row.dataset.search.toLowerCase().includes(query)
    && (statusFilter.value === "all" || row.dataset.status === statusFilter.value)
    && (priorityFilter.value === "all" || row.dataset.priority === priorityFilter.value));
}

function pageLink(label, disabled, active, action) {
  const item = document.createElement("li");
  item.className = `page-item${disabled ? " disabled" : ""}${active ? " active" : ""}`;
  item.innerHTML = `<a class="page-link" href="#">${label}</a>`;
  item.addEventListener("click", event => { event.preventDefault(); if (!disabled) action(); });
  pagination.appendChild(item);
}

function render() {
  const filtered = filteredRows();
  const pages = Math.max(1, Math.ceil(filtered.length / pageSize));
  currentPage = Math.min(currentPage, pages);
  rows.forEach(row => row.classList.add("d-none"));
  const start = (currentPage - 1) * pageSize;
  filtered.slice(start, start + pageSize).forEach(row => row.classList.remove("d-none"));
  empty.classList.toggle("d-none", filtered.length !== 0);
  const first = filtered.length ? start + 1 : 0;
  paginationInfo.textContent = `Mostrando ${first}-${Math.min(start + pageSize, filtered.length)} de ${filtered.length} solicitudes`;
  pagination.innerHTML = "";
  pageLink("Anterior", currentPage === 1, false, () => { currentPage--; render(); });
  for (let page = 1; page <= pages; page++) pageLink(page, false, page === currentPage, () => { currentPage = page; render(); });
  pageLink("Siguiente", currentPage === pages, false, () => { currentPage++; render(); });
}

[searchInput, statusFilter, priorityFilter].forEach(element => element.addEventListener(element.tagName === "INPUT" ? "input" : "change", () => { currentPage = 1; render(); }));
clearFilters.addEventListener("click", () => { searchInput.value = ""; statusFilter.value = "all"; priorityFilter.value = "all"; currentPage = 1; render(); });
render();
