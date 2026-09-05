/*
 * SkillBridge AI
 * Asignaciones - Resource Manager
 *
 * Datos y lógica temporal para el mockup.
 * En backend, los registros, filtros y paginación deberán obtenerse
 * desde Spring Boot / Spring Data.
 */

const assignments = [
  /* =========================
     PENDIENTES
     ========================= */

  {
    person: "Carlos Mendoza",
    initials: "CM",
    project: "Clínica AI",
    origin: "PM",
    weeklyHours: "12 h",
    rmApproval: "pending",
    pmApproval: "origin",
    status: "Pendiente RM",
    date: "04 Sep 2026",
    group: "pending"
  },
  {
    person: "Ana Torres",
    initials: "AT",
    project: "ERP Cloud",
    origin: "Colaborador",
    weeklyHours: "Por definir",
    rmApproval: "pending",
    pmApproval: "pending",
    status: "Pendiente RM",
    date: "04 Sep 2026",
    group: "pending"
  },
  {
    person: "Luis Ramos",
    initials: "LR",
    project: "DataHub",
    origin: "RM",
    weeklyHours: "16 h",
    rmApproval: "origin",
    pmApproval: "pending",
    status: "Pendiente PM",
    date: "03 Sep 2026",
    group: "pending"
  },
  {
    person: "Sofía Torres",
    initials: "ST",
    project: "Plataforma Retail",
    origin: "Colaborador",
    weeklyHours: "10 h",
    rmApproval: "approved",
    pmApproval: "pending",
    status: "Pendiente PM",
    date: "03 Sep 2026",
    group: "pending"
  },
  {
    person: "Diego Fernández",
    initials: "DF",
    project: "Migración Cloud",
    origin: "PM",
    weeklyHours: "20 h",
    rmApproval: "pending",
    pmApproval: "origin",
    status: "Pendiente RM",
    date: "02 Sep 2026",
    group: "pending"
  },
  {
    person: "Camila Herrera",
    initials: "CH",
    project: "Portal de Clientes",
    origin: "RM",
    weeklyHours: "12 h",
    rmApproval: "origin",
    pmApproval: "pending",
    status: "Pendiente PM",
    date: "02 Sep 2026",
    group: "pending"
  },
  {
    person: "Renato Silva",
    initials: "RS",
    project: "Clínica AI",
    origin: "Colaborador",
    weeklyHours: "Por definir",
    rmApproval: "pending",
    pmApproval: "pending",
    status: "Pendiente RM",
    date: "01 Sep 2026",
    group: "pending"
  },
  {
    person: "Lucía Paredes",
    initials: "LP",
    project: "ERP Cloud",
    origin: "PM",
    weeklyHours: "8 h",
    rmApproval: "pending",
    pmApproval: "origin",
    status: "Pendiente RM",
    date: "31 Ago 2026",
    group: "pending"
  },

  /* =========================
     ACTIVAS
     ========================= */

  {
    person: "María López",
    initials: "ML",
    project: "Sistema de Inventario",
    origin: "RM",
    weeklyHours: "16 h",
    rmApproval: "origin",
    pmApproval: "approved",
    status: "Activa",
    date: "28 Ago 2026",
    group: "active"
  },
  {
    person: "Pedro Díaz",
    initials: "PD",
    project: "Migración Cloud",
    origin: "PM",
    weeklyHours: "12 h",
    rmApproval: "approved",
    pmApproval: "origin",
    status: "Activa",
    date: "25 Ago 2026",
    group: "active"
  },
  {
    person: "Valeria Ruiz",
    initials: "VR",
    project: "Clínica AI",
    origin: "Colaborador",
    weeklyHours: "8 h",
    rmApproval: "approved",
    pmApproval: "approved",
    status: "Activa",
    date: "22 Ago 2026",
    group: "active"
  },
  {
    person: "Jorge Salazar",
    initials: "JS",
    project: "App Móvil Ventas",
    origin: "RM",
    weeklyHours: "20 h",
    rmApproval: "origin",
    pmApproval: "approved",
    status: "Activa",
    date: "20 Ago 2026",
    group: "active"
  },
  {
    person: "Mariana Vega",
    initials: "MV",
    project: "ERP Cloud",
    origin: "PM",
    weeklyHours: "16 h",
    rmApproval: "approved",
    pmApproval: "origin",
    status: "Activa",
    date: "18 Ago 2026",
    group: "active"
  },
  {
    person: "Daniel Acosta",
    initials: "DA",
    project: "DataHub",
    origin: "RM",
    weeklyHours: "12 h",
    rmApproval: "origin",
    pmApproval: "approved",
    status: "Activa",
    date: "16 Ago 2026",
    group: "active"
  },

  /* =========================
     HISTORIAL
     ========================= */

  {
    person: "Martín Vega",
    initials: "MV",
    project: "API Gateway",
    origin: "PM",
    weeklyHours: "16 h",
    rmApproval: "approved",
    pmApproval: "origin",
    status: "Finalizada",
    date: "30 Jul 2026",
    group: "history"
  },
  {
    person: "Natalia León",
    initials: "NL",
    project: "Dashboard Analytics",
    origin: "Colaborador",
    weeklyHours: "Por definir",
    rmApproval: "rejected",
    pmApproval: "not_required",
    status: "Rechazada",
    date: "25 Jul 2026",
    group: "history"
  },
  {
    person: "Andrea Campos",
    initials: "AC",
    project: "Portal de Clientes",
    origin: "RM",
    weeklyHours: "12 h",
    rmApproval: "origin",
    pmApproval: "approved",
    status: "Finalizada",
    date: "18 Jul 2026",
    group: "history"
  },
  {
    person: "Gonzalo Peña",
    initials: "GP",
    project: "Clínica AI",
    origin: "PM",
    weeklyHours: "10 h",
    rmApproval: "rejected",
    pmApproval: "origin",
    status: "Rechazada",
    date: "14 Jul 2026",
    group: "history"
  },
  {
    person: "Elena Rojas",
    initials: "ER",
    project: "Sistema de Inventario",
    origin: "Colaborador",
    weeklyHours: "8 h",
    rmApproval: "approved",
    pmApproval: "approved",
    status: "Finalizada",
    date: "08 Jul 2026",
    group: "history"
  }
];

const pageSize = 6;

let currentPage = 1;
let currentTab = "pending";

const assignmentBody =
  document.getElementById("assignmentBody");

const pagination =
  document.getElementById("pagination");

const paginationInfo =
  document.getElementById("paginationInfo");

const searchInput =
  document.getElementById("searchInput");

const originFilter =
  document.getElementById("originFilter");

const statusFilter =
  document.getElementById("statusFilter");

const clearFilters =
  document.getElementById("clearFilters");

const tabButtons =
  [...document.querySelectorAll(".assignment-tabs button")];

const metricPendingRM =
  document.getElementById("metricPendingRM");

const metricPendingPM =
  document.getElementById("metricPendingPM");

const metricCollaboratorRequests =
  document.getElementById("metricCollaboratorRequests");

const metricActive =
  document.getElementById("metricActive");

const tabPendingCount =
  document.getElementById("tabPendingCount");

const tabActiveCount =
  document.getElementById("tabActiveCount");

const tabHistoryCount =
  document.getElementById("tabHistoryCount");


const statusOptions = {
  pending: [
    ["all", "Todos los estados"],
    ["Pendiente RM", "Pendiente RM"],
    ["Pendiente PM", "Pendiente PM"]
  ],
  active: [
    ["all", "Todos los estados"],
    ["Activa", "Activa"]
  ],
  history: [
    ["all", "Todos los estados"],
    ["Finalizada", "Finalizada"],
    ["Rechazada", "Rechazada"]
  ]
};


function originLabel(origin) {
  const labels = {
    PM: "Propuesta del PM",
    RM: "Propuesta del RM",
    Colaborador: "Solicitud del colaborador"
  };

  return labels[origin] || origin;
}


function originBadgeClass(origin) {
  const classes = {
    PM: "bg-blue-lt",
    RM: "bg-azure-lt",
    Colaborador: "bg-purple-lt"
  };

  return classes[origin] || "bg-secondary-lt";
}


function statusBadgeClass(status) {
  const classes = {
    "Pendiente RM": "bg-yellow-lt",
    "Pendiente PM": "bg-blue-lt",
    "Activa": "bg-green-lt",
    "Finalizada": "bg-secondary-lt",
    "Rechazada": "bg-red-lt"
  };

  return classes[status] || "bg-secondary-lt";
}


function approvalBadge(role, value) {
  let badgeClass = "bg-secondary-lt text-secondary";

  if (value === "origin" || value === "approved") {
    badgeClass = "bg-green-lt text-green";
  } else if (value === "pending") {
    badgeClass = role === "RM" ? "bg-yellow-lt" : "bg-blue-lt";
  } else if (value === "rejected") {
    badgeClass = "bg-red-lt text-red";
  }

  return `<span class="badge ${badgeClass}">${role}</span>`;
}


function approvals(row) {
  return `
    <div class="approval-mini">
      ${approvalBadge("PM", row.pmApproval)}
      ${approvalBadge("RM", row.rmApproval)}
    </div>
  `;
}


function actionButtons(row) {
  if (row.status === "Pendiente RM") {
    return `
      <a href="#"
         class="btn btn-primary btn-sm">
        Revisar
      </a>
    `;
  }

  if (row.status === "Pendiente PM") {
    return `
      <a href="#"
         class="btn btn-outline-secondary btn-sm">
        Ver
      </a>
    `;
  }

  if (row.status === "Activa") {
    return `
      <span class="actions-mini">
        <a href="#"
           class="btn btn-outline-secondary btn-sm">
          Ver
        </a>

        <a href="#"
           class="btn btn-outline-danger btn-sm">
          Finalizar
        </a>
      </span>
    `;
  }

  return `
    <a href="#"
       class="btn btn-outline-secondary btn-sm">
      Ver
    </a>
  `;
}


function updateSummaryMetrics() {
  metricPendingRM.textContent =
    assignments.filter(row =>
      row.status === "Pendiente RM"
    ).length;

  metricPendingPM.textContent =
    assignments.filter(row =>
      row.status === "Pendiente PM"
    ).length;

  metricCollaboratorRequests.textContent =
    assignments.filter(row =>
      row.group === "pending" &&
      row.origin === "Colaborador"
    ).length;

  metricActive.textContent =
    assignments.filter(row =>
      row.group === "active"
    ).length;

  tabPendingCount.textContent =
    assignments.filter(row =>
      row.group === "pending"
    ).length;

  tabActiveCount.textContent =
    assignments.filter(row =>
      row.group === "active"
    ).length;

  tabHistoryCount.textContent =
    assignments.filter(row =>
      row.group === "history"
    ).length;
}


function updateStatusFilter() {
  statusFilter.innerHTML =
    statusOptions[currentTab]
      .map(([value, label]) => `
        <option value="${value}">
          ${label}
        </option>
      `)
      .join("");
}


function getFilteredRows() {
  const search =
    searchInput.value.trim().toLowerCase();

  const origin =
    originFilter.value;

  const status =
    statusFilter.value;

  return assignments.filter(row => {
    const matchesTab =
      row.group === currentTab;

    const matchesSearch =
      `${row.person} ${row.project}`.toLowerCase().includes(search);

    const matchesOrigin =
      origin === "all" ||
      row.origin === origin;

    const matchesStatus =
      status === "all" ||
      row.status === status;

    return matchesTab &&
      matchesSearch &&
      matchesOrigin &&
      matchesStatus;
  });
}


function renderRows() {
  const filtered =
    getFilteredRows();

  const totalPages =
    Math.max(1, Math.ceil(filtered.length / pageSize));

  if (currentPage > totalPages) {
    currentPage = totalPages;
  }

  const start =
    (currentPage - 1) * pageSize;

  const visible =
    filtered.slice(start, start + pageSize);

  assignmentBody.innerHTML = visible.length
    ? visible.map(row => `
      <tr>

        <td>
          <div class="d-flex align-items-center gap-2">
            <span class="avatar avatar-sm-custom bg-blue-lt text-blue">
              ${row.initials}
            </span>

            <span class="fw-semibold">
              ${row.person}
            </span>
          </div>
        </td>

        <td>
          <span class="fw-semibold">
            ${row.project}
          </span>
        </td>

        <td>
          <span class="badge ${originBadgeClass(row.origin)}">
            ${originLabel(row.origin)}
          </span>
        </td>

        <td>
          ${row.weeklyHours}
        </td>

        <td>
          ${approvals(row)}
        </td>

        <td>
          <span class="badge ${statusBadgeClass(row.status)}">
            ${row.status}
          </span>
        </td>

        <td>
          ${row.date}
        </td>

        <td class="text-end">
          ${actionButtons(row)}
        </td>

      </tr>
    `).join("")
    : `
      <tr>
        <td colspan="8"
            class="text-center text-secondary py-5">
          No se encontraron registros con los filtros seleccionados.
        </td>
      </tr>
    `;

  const first =
    filtered.length ? start + 1 : 0;

  const last =
    Math.min(start + pageSize, filtered.length);

  paginationInfo.textContent =
    `Mostrando ${first}-${last} de ${filtered.length} registros`;

  renderPagination(totalPages);
}


function renderPagination(totalPages) {
  pagination.innerHTML = "";

  const previous =
    document.createElement("li");

  previous.className =
    `page-item ${currentPage === 1 ? "disabled" : ""}`;

  previous.innerHTML =
    '<a class="page-link" href="#">Anterior</a>';

  previous.addEventListener("click", event => {
    event.preventDefault();

    if (currentPage > 1) {
      currentPage--;
      renderRows();
    }
  });

  pagination.appendChild(previous);


  for (let page = 1; page <= totalPages; page++) {
    const item =
      document.createElement("li");

    item.className =
      `page-item ${page === currentPage ? "active" : ""}`;

    item.innerHTML =
      `<a class="page-link" href="#">${page}</a>`;

    item.addEventListener("click", event => {
      event.preventDefault();

      currentPage = page;
      renderRows();
    });

    pagination.appendChild(item);
  }


  const next =
    document.createElement("li");

  next.className =
    `page-item ${currentPage === totalPages ? "disabled" : ""}`;

  next.innerHTML =
    '<a class="page-link" href="#">Siguiente</a>';

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
    tabButtons.forEach(tab =>
      tab.classList.remove("active")
    );

    button.classList.add("active");

    currentTab =
      button.dataset.tab;

    currentPage = 1;

    updateStatusFilter();
    renderRows();
  });
});


[
  searchInput,
  originFilter,
  statusFilter
].forEach(element => {
  const eventName =
    element.tagName === "INPUT"
      ? "input"
      : "change";

  element.addEventListener(eventName, () => {
    currentPage = 1;
    renderRows();
  });
});


clearFilters.addEventListener("click", () => {
  searchInput.value = "";
  originFilter.value = "all";

  updateStatusFilter();

  currentPage = 1;
  renderRows();
});


updateSummaryMetrics();
updateStatusFilter();
renderRows();
