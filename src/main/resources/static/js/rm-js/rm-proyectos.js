/*
 * SkillBridge AI
 * Proyectos - Resource Manager
 *
 * Datos y lógica temporal para previsualización.
 * Al conectar el backend, los proyectos y la paginación deberán
 * obtenerse desde Spring Boot / Spring Data.
 */

const projects = [
  {
    name: "Sistema de Inventario",
    description: "Sistema integral de gestión de inventario con seguimiento en tiempo real y reportes analíticos.",
    status: "Activo",
    priority: "Alta",
    pm: "Carlos Méndez",
    currentTeam: 5,
    requiredTeam: 8,
    pendingRm: 2,
    budgetAssigned: 52000,
    endDate: "15 Dic 2026"
  },
  {
    name: "App Móvil Ventas",
    description: "Aplicación móvil para gestión de ventas en campo, sincronización offline y métricas comerciales.",
    status: "Activo",
    priority: "Media",
    pm: "Valeria Soto",
    currentTeam: 3,
    requiredTeam: 4,
    pendingRm: 1,
    budgetAssigned: 36000,
    endDate: "20 Ene 2027"
  },
  {
    name: "Portal de Clientes",
    description: "Portal de autoservicio para facturas, soporte de tickets y seguimiento de pedidos.",
    status: "En revisión",
    priority: "Media",
    pm: "Marco Ruiz",
    currentTeam: 0,
    requiredTeam: 6,
    pendingRm: 0,
    budgetAssigned: 48000,
    endDate: "01 Mar 2027"
  },
  {
    name: "Migración Cloud",
    description: "Migración de infraestructura on-premise a AWS con modernización y automatización CI/CD.",
    status: "Activo",
    priority: "Alta",
    pm: "Lucía Herrera",
    currentTeam: 4,
    requiredTeam: 4,
    pendingRm: 1,
    budgetAssigned: 60000,
    endDate: "30 Nov 2026"
  },
  {
    name: "Dashboard Analytics",
    description: "Panel corporativo de analítica avanzada con visualización de datos y reportes personalizables.",
    status: "En espera",
    priority: "Baja",
    pm: "Javier Salas",
    currentTeam: 2,
    requiredTeam: 5,
    pendingRm: 0,
    budgetAssigned: 41000,
    endDate: "15 Feb 2027"
  },
  {
    name: "API Gateway",
    description: "Gateway centralizado con autenticación OAuth2, rate limiting y monitoreo de servicios internos.",
    status: "Finalizado",
    priority: "Baja",
    pm: "Paola Medina",
    currentTeam: 4,
    requiredTeam: 4,
    pendingRm: 0,
    budgetAssigned: 39000,
    endDate: "30 Ago 2026"
  },
  {
    name: "Plataforma RRHH",
    description: "Plataforma interna para procesos de selección, onboarding y seguimiento de desempeño.",
    status: "Rechazado",
    priority: "Media",
    pm: "Diego Torres",
    currentTeam: 1,
    requiredTeam: 5,
    pendingRm: 0,
    budgetAssigned: 45000,
    endDate: "12 Abr 2027"
  },
  {
    name: "Motor de Recomendaciones",
    description: "Servicio de recomendaciones personalizadas basado en comportamiento y reglas de negocio.",
    status: "Cancelado",
    priority: "Baja",
    pm: "Andrea León",
    currentTeam: 0,
    requiredTeam: 4,
    pendingRm: 0,
    budgetAssigned: 33000,
    endDate: "—"
  }
];

const pageSize = 6;
let currentPage = 1;

const statusMap = {
  Activo: {
    badgeClass: "bg-green-lt",
    label: "Activo"
  },
  "En revisión": {
    badgeClass: "bg-yellow-lt",
    label: "En revisión"
  },
  Rechazado: {
    badgeClass: "bg-red-lt",
    label: "Rechazado"
  },
  "En espera": {
    badgeClass: "bg-orange-lt",
    label: "En espera"
  },
  Cancelado: {
    badgeClass: "bg-secondary-lt",
    label: "Cancelado"
  },
  Finalizado: {
    badgeClass: "bg-blue-lt",
    label: "Finalizado"
  }
};

const priorityMap = {
  Alta: "bg-red-lt text-red",
  Media: "bg-yellow-lt text-yellow",
  Baja: "bg-blue-lt text-blue"
};

const searchInput = document.getElementById("searchInput");
const statusFilter = document.getElementById("statusFilter");
const priorityFilter = document.getElementById("priorityFilter");
const vacancyFilter = document.getElementById("vacancyFilter");
const clearFilters = document.getElementById("clearFilters");

const projectGrid = document.getElementById("projectGrid");
const pagination = document.getElementById("pagination");
const paginationInfo = document.getElementById("paginationInfo");

const metricTotal = document.getElementById("metricTotal");
const metricVacancies = document.getElementById("metricVacancies");
const metricPending = document.getElementById("metricPending");
const metricInReview = document.getElementById("metricInReview");

function formatCurrency(value) {
  return new Intl.NumberFormat("es-PE", {
    style: "currency",
    currency: "PEN",
    maximumFractionDigits: 0
  }).format(value);
}

function vacanciesFor(project) {
  return Math.max(0, project.requiredTeam - project.currentTeam);
}

function updateSummaryMetrics() {
  const statesWithStaffing = ["En revisión", "Activo", "En espera"];
  const activeForVacancies = projects.filter(project =>
    statesWithStaffing.includes(project.status)
  );

  metricTotal.textContent = projects.length;

  metricVacancies.textContent = activeForVacancies.filter(project =>
    vacanciesFor(project) > 0
  ).length;

  metricPending.textContent = projects.reduce(
    (total, project) => total + project.pendingRm,
    0
  );

  metricInReview.textContent = projects.filter(project =>
    project.status === "En revisión"
  ).length;
}

function getFilteredProjects() {
  const search = searchInput.value.trim().toLowerCase();
  const status = statusFilter.value;
  const priority = priorityFilter.value;
  const vacancy = vacancyFilter.value;

  return projects.filter(project => {
    const matchesSearch =
      project.name.toLowerCase().includes(search) ||
      project.pm.toLowerCase().includes(search);

    const matchesStatus =
      status === "all" || project.status === status;

    const matchesPriority =
      priority === "all" || project.priority === priority;

    const vacancies = vacanciesFor(project);

    const matchesVacancy =
      vacancy === "all" ||
      (vacancy === "with" && vacancies > 0) ||
      (vacancy === "full" && vacancies === 0);

    return matchesSearch &&
      matchesStatus &&
      matchesPriority &&
      matchesVacancy;
  });
}

function renderProjects() {
  const filtered = getFilteredProjects();

  const totalPages =
    Math.max(1, Math.ceil(filtered.length / pageSize));

  if (currentPage > totalPages) {
    currentPage = totalPages;
  }

  const start =
    (currentPage - 1) * pageSize;

  const visible =
    filtered.slice(start, start + pageSize);

  projectGrid.innerHTML = visible.length
    ? visible.map(project => {
        const vacancies = vacanciesFor(project);

        const status =
          statusMap[project.status] || {
            badgeClass: "bg-secondary-lt",
            label: project.status
          };

        const pendingMarkup = project.pendingRm > 0
          ? `<span class="badge bg-yellow-lt">
               ${project.pendingRm}
               ${project.pendingRm === 1 ? "pendiente RM" : "pendientes RM"}
             </span>`
          : `<span class="badge bg-green-lt">
               Sin pendientes RM
             </span>`;

        const projectAction = project.status === "En revisión"
          ? {
              url: rmViewUrl(
                "rm-revision-proyecto.html",
                "/rm/proyectos/revision"
              ),
              label: "Revisar proyecto"
            }
          : {
              url: rmViewUrl(
                "rm-detalle-proyecto.html",
                "/rm/proyectos/detalle"
              ),
              label: "Ver proyecto"
            };

        return `
          <div class="col-lg-6">
            <div class="card project-card">

              <div class="card-body">

                <div class="d-flex justify-content-between align-items-start gap-3">

                  <div>
                    <h3 class="project-title">
                      ${project.name}
                    </h3>

                    <div class="text-secondary small mt-1">
                      PM:
                      <span class="fw-medium text-body">
                        ${project.pm}
                      </span>
                    </div>
                  </div>

                  <span class="badge ${status.badgeClass}">
                    ${status.label}
                  </span>

                </div>


                <div class="project-desc">
                  ${project.description}
                </div>


                <div class="meta-grid">

                  <div>
                    <div class="meta-label">
                      Equipo
                    </div>

                    <div class="meta-value">
                      ${project.currentTeam}/${project.requiredTeam} integrantes
                    </div>
                  </div>

                  <div>
                    <div class="meta-label">
                      Vacantes
                    </div>

                    <div class="meta-value">
                      ${vacancies}
                    </div>
                  </div>

                  <div>
                    <div class="meta-label">
                      Pendientes del RM
                    </div>

                    <div class="meta-value">
                      ${project.pendingRm}
                    </div>
                  </div>

                  <div>
                    <div class="meta-label">
                      Fin estimado
                    </div>

                    <div class="meta-value">
                      ${project.endDate}
                    </div>
                  </div>

                  <div>
                    <div class="meta-label">
                      Prioridad
                    </div>

                    <div class="meta-value">
                      <span class="badge priority-badge ${priorityMap[project.priority]}">
                        ${project.priority}
                      </span>
                    </div>
                  </div>

                </div>


                <div class="project-budget">

                  <div class="meta-label">
                    Presupuesto asignado
                  </div>

                  <div class="meta-value">
                    ${formatCurrency(project.budgetAssigned)}
                  </div>

                </div>


                <div class="project-footer">

                  <div>
                    ${pendingMarkup}
                  </div>

                  <a href="${projectAction.url}"
                     class="btn btn-outline-primary btn-sm">
                    ${projectAction.label}
                  </a>

                </div>

              </div>

            </div>
          </div>
        `;
      }).join("")
    : `
      <div class="col-12">
        <div class="card">
          <div class="empty-state">
            No se encontraron proyectos con los filtros seleccionados.
          </div>
        </div>
      </div>
    `;

  const firstItem =
    filtered.length ? start + 1 : 0;

  const lastItem =
    Math.min(start + pageSize, filtered.length);

  paginationInfo.textContent =
    `Mostrando ${firstItem}-${lastItem} de ${filtered.length} proyectos`;

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
      renderProjects();
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

      renderProjects();
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
      renderProjects();
    }
  });

  pagination.appendChild(next);
}

[searchInput, statusFilter, priorityFilter, vacancyFilter].forEach(element => {
  const eventName =
    element.tagName === "INPUT"
      ? "input"
      : "change";

  element.addEventListener(eventName, () => {
    currentPage = 1;
    renderProjects();
  });
});

clearFilters.addEventListener("click", () => {
  searchInput.value = "";
  statusFilter.value = "all";
  priorityFilter.value = "all";
  vacancyFilter.value = "all";

  currentPage = 1;

  renderProjects();
});

updateSummaryMetrics();
renderProjects();
