const rows = Array.from(document.querySelectorAll(".certificate-row"));
const searchInput = document.getElementById("searchInput");
const skillFilter = document.getElementById("skillFilter");
const clearFilters = document.getElementById("clearFilters");
const empty = document.getElementById("emptyCertificates");
const pagination = document.getElementById("pagination");
const paginationInfo = document.getElementById("paginationInfo");
const pageSize = 6;
let currentPage = 1;

function filteredRows() {
  const query = searchInput.value.trim().toLowerCase();
  return rows.filter(row => row.dataset.search.toLowerCase().includes(query)
    && (skillFilter.value === "all" || row.dataset.skill === skillFilter.value));
}
function link(label, disabled, active, action) {
  const li = document.createElement("li");
  li.className = `page-item${disabled ? " disabled" : ""}${active ? " active" : ""}`;
  li.innerHTML = `<a class="page-link" href="#">${label}</a>`;
  li.addEventListener("click", event => { event.preventDefault(); if (!disabled) action(); });
  pagination.appendChild(li);
}
function render() {
  const filtered = filteredRows();
  const pages = Math.max(1, Math.ceil(filtered.length / pageSize));
  currentPage = Math.min(currentPage, pages);
  rows.forEach(row => row.classList.add("d-none"));
  const start = (currentPage - 1) * pageSize;
  filtered.slice(start, start + pageSize).forEach(row => row.classList.remove("d-none"));
  empty.classList.toggle("d-none", filtered.length !== 0);
  paginationInfo.textContent = `Mostrando ${filtered.length ? start + 1 : 0}-${Math.min(start + pageSize, filtered.length)} de ${filtered.length} certificados`;
  pagination.innerHTML = "";
  link("Anterior", currentPage === 1, false, () => { currentPage--; render(); });
  for (let page = 1; page <= pages; page++) link(page, false, page === currentPage, () => { currentPage = page; render(); });
  link("Siguiente", currentPage === pages, false, () => { currentPage++; render(); });
}
[searchInput, skillFilter].forEach(element => element.addEventListener(element.tagName === "INPUT" ? "input" : "change", () => { currentPage = 1; render(); }));
clearFilters.addEventListener("click", () => { searchInput.value = ""; skillFilter.value = "all"; currentPage = 1; render(); });
render();
