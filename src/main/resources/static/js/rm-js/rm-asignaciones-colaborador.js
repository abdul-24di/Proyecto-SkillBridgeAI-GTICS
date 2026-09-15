/*
 * SkillBridge AI
 * Asignaciones de un colaborador - Resource Manager
 */

const collaboratorAssignments = [
  { project:"Clínica AI", role:"Backend Developer", pm:"Juan Pérez", weeklyHours:"16 h", start:"15 Ago 2026", end:"15 Dic 2026", status:"Activa", year:"2026", group:"active" },
  { project:"ERP Cloud", role:"Backend Developer", pm:"María Ruiz", weeklyHours:"8 h", start:"01 Ago 2026", end:"30 Nov 2026", status:"Activa", year:"2026", group:"active" },
  { project:"API Gateway", role:"Backend Developer", pm:"Carlos Méndez", weeklyHours:"12 h", start:"05 Mar 2026", end:"30 Jun 2026", status:"Finalizada", year:"2026", group:"history" },
  { project:"Portal de Clientes", role:"Backend Developer", pm:"Diego Torres", weeklyHours:"16 h", start:"10 Ene 2026", end:"28 Feb 2026", status:"Finalizada", year:"2026", group:"history" },
  { project:"DataHub", role:"API Developer", pm:"Ana Salazar", weeklyHours:"12 h", start:"03 Sep 2025", end:"15 Dic 2025", status:"Finalizada", year:"2025", group:"history" },
  { project:"Integración CRM", role:"Java Developer", pm:"Lucía Vega", weeklyHours:"20 h", start:"01 Abr 2025", end:"30 Jul 2025", status:"Finalizada", year:"2025", group:"history" },
  { project:"Facturación Digital", role:"Backend Developer", pm:"Marco Ruiz", weeklyHours:"16 h", start:"10 Ene 2025", end:"20 Mar 2025", status:"Finalizada", year:"2025", group:"history" }
];

const pageSize = 5;
let currentPage = 1;
let currentTab = "active";

const assignmentBody = document.getElementById("assignmentBody");
const assignmentSearch = document.getElementById("assignmentSearch");
const statusFilter = document.getElementById("statusFilter");
const yearFilter = document.getElementById("yearFilter");
const clearFilters = document.getElementById("clearFilters");
const pagination = document.getElementById("pagination");
const paginationInfo = document.getElementById("paginationInfo");
const tabButtons = [...document.querySelectorAll(".collaborator-tabs button")];

const activeMetric = document.getElementById("activeMetric");
const historyMetric = document.getElementById("historyMetric");
const activeTabCount = document.getElementById("activeTabCount");
const historyTabCount = document.getElementById("historyTabCount");

const statusOptions = {
  active: [["all", "Todos"], ["Activa", "Activa"]],
  history: [["all", "Todos"], ["Finalizada", "Finalizada"]]
};

function updateCounts() {
  const activeCount = collaboratorAssignments.filter(item => item.group === "active").length;
  const historyCount = collaboratorAssignments.filter(item => item.group === "history").length;
  activeMetric.textContent = activeCount;
  historyMetric.textContent = historyCount;
  activeTabCount.textContent = activeCount;
  historyTabCount.textContent = historyCount;
}

function updateStatusFilter() {
  statusFilter.innerHTML = statusOptions[currentTab]
    .map(([value, label]) => `<option value="${value}">${label}</option>`)
    .join("");
}

function statusBadge(status) {
  return status === "Activa" ? "bg-green-lt" : "bg-secondary-lt";
}

function actionButtons(item) {
  if (item.status === "Activa") {
    return `
      <span class="assignment-action-group">
        <a href="${rmViewUrl(
          "rm-detalle-asignacion-activa.html",
          "/rm/asignaciones/activa"
        )}" class="btn btn-outline-secondary btn-sm">Ver</a>
        <button type="button"
                class="btn btn-outline-danger btn-sm"
                data-bs-toggle="modal"
                data-bs-target="#finishCollaboratorAssignmentModal"
                data-collaborator="Carlos Mendoza"
                data-project="${item.project}"
                data-role="${item.role}"
                data-start="${item.start}">
          Finalizar
        </button>
      </span>
    `;
  }
  return `<a href="#" class="btn btn-outline-secondary btn-sm">Ver</a>`;
}

function filteredRows() {
  const search = assignmentSearch.value.trim().toLowerCase();
  const status = statusFilter.value;
  const year = yearFilter.value;

  return collaboratorAssignments.filter(item => {
    const searchable = `${item.project} ${item.role} ${item.pm}`.toLowerCase();
    return item.group === currentTab
      && searchable.includes(search)
      && (status === "all" || item.status === status)
      && (year === "all" || item.year === year);
  });
}

function renderRows() {
  const filtered = filteredRows();
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));

  if (currentPage > totalPages) currentPage = totalPages;

  const start = (currentPage - 1) * pageSize;
  const visible = filtered.slice(start, start + pageSize);

  assignmentBody.innerHTML = visible.length
    ? visible.map(item => `
      <tr>
        <td class="fw-semibold">${item.project}</td>
        <td>${item.role}</td>
        <td>${item.pm}</td>
        <td>${item.weeklyHours}</td>
        <td>
          <div>${item.start}</div>
          <div class="small text-secondary">hasta ${item.end}</div>
        </td>
        <td><span class="badge ${statusBadge(item.status)}">${item.status}</span></td>
        <td class="text-end">${actionButtons(item)}</td>
      </tr>
    `).join("")
    : `
      <tr>
        <td colspan="7" class="text-center text-secondary py-5">
          No se encontraron asignaciones con los filtros seleccionados.
        </td>
      </tr>
    `;

  const first = filtered.length ? start + 1 : 0;
  const last = Math.min(start + pageSize, filtered.length);
  paginationInfo.textContent = `Mostrando ${first}-${last} de ${filtered.length} registros`;

  renderPagination(totalPages);
}

function renderPagination(totalPages) {
  pagination.innerHTML = "";

  const previous = document.createElement("li");
  previous.className = `page-item ${currentPage === 1 ? "disabled" : ""}`;
  previous.innerHTML = '<a class="page-link" href="#">Anterior</a>';
  previous.addEventListener("click", event => {
    event.preventDefault();
    if (currentPage > 1) {
      currentPage--;
      renderRows();
    }
  });
  pagination.appendChild(previous);

  for (let page = 1; page <= totalPages; page++) {
    const item = document.createElement("li");
    item.className = `page-item ${page === currentPage ? "active" : ""}`;
    item.innerHTML = `<a class="page-link" href="#">${page}</a>`;
    item.addEventListener("click", event => {
      event.preventDefault();
      currentPage = page;
      renderRows();
    });
    pagination.appendChild(item);
  }

  const next = document.createElement("li");
  next.className = `page-item ${currentPage === totalPages ? "disabled" : ""}`;
  next.innerHTML = '<a class="page-link" href="#">Siguiente</a>';
  next.addEventListener("click", event => {
    event.preventDefault();
    if (currentPage < totalPages) {
      currentPage++;
      renderRows();
    }
  });
  pagination.appendChild(next);
}

tabButtons.forEach(button => {
  button.addEventListener("click", () => {
    tabButtons.forEach(item => item.classList.remove("active"));
    button.classList.add("active");
    currentTab = button.dataset.tab;
    currentPage = 1;
    updateStatusFilter();
    renderRows();
  });
});

[assignmentSearch, statusFilter, yearFilter].forEach(element => {
  const eventName = element.tagName === "INPUT" ? "input" : "change";
  element.addEventListener(eventName, () => {
    currentPage = 1;
    renderRows();
  });
});

clearFilters.addEventListener("click", () => {
  assignmentSearch.value = "";
  yearFilter.value = "all";
  updateStatusFilter();
  currentPage = 1;
  renderRows();
});

updateCounts();
updateStatusFilter();
renderRows();
