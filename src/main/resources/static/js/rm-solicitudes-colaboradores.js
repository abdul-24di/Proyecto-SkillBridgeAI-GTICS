/*
 * SkillBridge AI
 * Solicitudes de colaboradores - RM
 *
 * Lógica temporal para previsualización de filtros y paginación.
 * Cuando la vista se conecte al backend, esta lógica puede migrarse
 * a consultas paginadas con Spring Data / Pageable.
 */

const requests = [
  {
    project: "Sistema de Inventario",
    pm: "Carlos Méndez",
    requested: 2,
    priority: "Alta",
    skills: ["Java", "Spring Boot", "React", "MySQL", "Docker", "AWS"],
    status: "Pendiente",
    date: "25 Ago 2026"
  },
  {
    project: "Portal de Clientes",
    pm: "Marco Ruiz",
    requested: 3,
    priority: "Media",
    skills: ["React", "UX/UI", "Node.js"],
    status: "En atención",
    date: "24 Ago 2026"
  },
  {
    project: "ERP Cloud",
    pm: "María Ruiz",
    requested: 1,
    priority: "Baja",
    skills: ["AWS", "Kubernetes"],
    status: "Atendida",
    date: "22 Ago 2026"
  },
  {
    project: "Clínica AI",
    pm: "Juan Pérez",
    requested: 2,
    priority: "Alta",
    skills: ["Kubernetes", "Python"],
    status: "Pendiente",
    date: "21 Ago 2026"
  },
  {
    project: "Dashboard Analytics",
    pm: "Javier Salas",
    requested: 2,
    priority: "Media",
    skills: ["Python", "Power BI"],
    status: "En atención",
    date: "20 Ago 2026"
  },
  {
    project: "Plataforma RRHH",
    pm: "Diego Torres",
    requested: 1,
    priority: "Baja",
    skills: ["React", "UX/UI"],
    status: "Pendiente",
    date: "19 Ago 2026"
  },
  {
    project: "App Móvil Ventas",
    pm: "Valeria Soto",
    requested: 1,
    priority: "Media",
    skills: ["Flutter", "Firebase"],
    status: "Atendida",
    date: "17 Ago 2026"
  },
  {
    project: "Migración Cloud",
    pm: "Lucía Herrera",
    requested: 1,
    priority: "Alta",
    skills: ["AWS", "Docker"],
    status: "Atendida",
    date: "15 Ago 2026"
  }
];

let currentPage = 1;
const pageSize = 6;

const body = document.getElementById("requestsBody");
const pagination = document.getElementById("pagination");
const paginationInfo = document.getElementById("paginationInfo");
const searchInput = document.getElementById("searchInput");
const statusFilter = document.getElementById("statusFilter");
const skillFilter = document.getElementById("skillFilter");
const priorityFilter = document.getElementById("priorityFilter");
const clearFilters = document.getElementById("clearFilters");

function priorityBadgeClass(priority) {
  if (priority === "Alta") {
    return "bg-red-lt text-red";
  }

  if (priority === "Media") {
    return "bg-yellow-lt text-yellow";
  }

  return "bg-blue-lt text-blue";
}

function badgeClass(status) {
  if (status === "Pendiente") {
    return "bg-yellow-lt";
  }

  if (status === "En atención") {
    return "bg-blue-lt";
  }

  return "bg-green-lt";
}

function actionButton(status) {
  if (status === "Pendiente") {
    return '<a href="#" class="btn btn-primary btn-sm">Atender</a>';
  }

  if (status === "En atención") {
    return '<a href="#" class="btn btn-outline-primary btn-sm">Continuar</a>';
  }

  return '<a href="#" class="btn btn-outline-secondary btn-sm">Ver</a>';
}

function getFilteredRows() {
  const query = searchInput.value.trim().toLowerCase();
  const status = statusFilter.value;
  const skill = skillFilter.value;
  const priority = priorityFilter.value;

  return requests.filter(request => {
    const searchableText = [
      request.project,
      request.pm,
      ...request.skills
    ].join(" ").toLowerCase();

    return searchableText.includes(query)
      && (status === "all" || request.status === status)
      && (skill === "all" || request.skills.includes(skill))
      && (priority === "all" || request.priority === priority);
  });
}

function renderTable() {
  const filtered = getFilteredRows();
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));

  if (currentPage > totalPages) {
    currentPage = totalPages;
  }

  const start = (currentPage - 1) * pageSize;
  const visible = filtered.slice(start, start + pageSize);

  body.innerHTML = visible.length
    ? visible.map(request => `
      <tr>
        <td class="fw-semibold">${request.project}</td>

        <td>
          <span class="badge priority-badge ${priorityBadgeClass(request.priority)}">
            ${request.priority}
          </span>
        </td>

        <td>${request.pm}</td>

        <td>
          <span class="badge bg-blue-lt">
            ${request.requested}
            ${request.requested === 1 ? "colaborador" : "colaboradores"}
          </span>
        </td>

        <td>
          ${request.skills
            .slice(0, 3)
            .map(skill => `
              <span class="badge bg-secondary-lt skill-chip">
                ${skill}
              </span>
            `)
            .join("")}

          ${request.skills.length > 3
            ? `<span class="text-secondary small">+${request.skills.length - 3}</span>`
            : ""}
        </td>

        <td>
          <span class="badge ${badgeClass(request.status)}">
            ${request.status}
          </span>
        </td>

        <td>${request.date}</td>

        <td class="text-end">
          ${actionButton(request.status)}
        </td>
      </tr>
    `).join("")
    : `
      <tr>
        <td colspan="8"
            class="text-center text-secondary py-5">
          No se encontraron solicitudes con los filtros seleccionados.
        </td>
      </tr>
    `;

  const first = filtered.length ? start + 1 : 0;
  const last = Math.min(start + pageSize, filtered.length);

  paginationInfo.textContent =
    `Mostrando ${first}-${last} de ${filtered.length} solicitudes`;

  renderPagination(totalPages);
}

function renderPagination(totalPages) {
  pagination.innerHTML = "";

  const previous = document.createElement("li");
  previous.className =
    `page-item ${currentPage === 1 ? "disabled" : ""}`;

  previous.innerHTML =
    '<a class="page-link" href="#">Anterior</a>';

  previous.addEventListener("click", event => {
    event.preventDefault();

    if (currentPage > 1) {
      currentPage--;
      renderTable();
    }
  });

  pagination.appendChild(previous);

  for (let page = 1; page <= totalPages; page++) {
    const item = document.createElement("li");

    item.className =
      `page-item ${page === currentPage ? "active" : ""}`;

    item.innerHTML =
      `<a class="page-link" href="#">${page}</a>`;

    item.addEventListener("click", event => {
      event.preventDefault();
      currentPage = page;
      renderTable();
    });

    pagination.appendChild(item);
  }

  const next = document.createElement("li");

  next.className =
    `page-item ${currentPage === totalPages ? "disabled" : ""}`;

  next.innerHTML =
    '<a class="page-link" href="#">Siguiente</a>';

  next.addEventListener("click", event => {
    event.preventDefault();

    if (currentPage < totalPages) {
      currentPage++;
      renderTable();
    }
  });

  pagination.appendChild(next);
}

[searchInput, statusFilter, skillFilter, priorityFilter].forEach(element => {
  const eventName =
    element.tagName === "INPUT" ? "input" : "change";

  element.addEventListener(eventName, () => {
    currentPage = 1;
    renderTable();
  });
});

clearFilters.addEventListener("click", () => {
  searchInput.value = "";
  statusFilter.value = "all";
  skillFilter.value = "all";
  priorityFilter.value = "all";

  currentPage = 1;

  renderTable();
});

renderTable();
