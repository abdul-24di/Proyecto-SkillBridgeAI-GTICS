/*
 * SkillBridge AI
 * Reporte de recursos - Resource Manager
 *
 * Las horas representadas aquí son horas trabajadas mensuales
 * derivadas de tareas estimadas. No son disponibilidad ni carga.
 */

const reports = {
  "2026-09": [
    {
      project: "Clínica AI",
      status: "Activo",
      priority: "Alta",
      budget: 40000,
      hours: 428,
      collaborators: 4
    },
    {
      project: "ERP Cloud",
      status: "Activo",
      priority: "Media",
      budget: 45000,
      hours: 512,
      collaborators: 5
    },
    {
      project: "Sistema de Inventario",
      status: "Activo",
      priority: "Alta",
      budget: 52000,
      hours: 390,
      collaborators: 5
    },
    {
      project: "Plataforma RRHH",
      status: "En espera",
      priority: "Baja",
      budget: 38000,
      hours: 176,
      collaborators: 2
    }
  ],
  "2026-08": [
    {
      project: "Clínica AI",
      status: "Activo",
      priority: "Alta",
      budget: 40000,
      hours: 302,
      collaborators: 3
    },
    {
      project: "ERP Cloud",
      status: "Activo",
      priority: "Media",
      budget: 45000,
      hours: 476,
      collaborators: 5
    },
    {
      project: "Sistema de Inventario",
      status: "Activo",
      priority: "Alta",
      budget: 52000,
      hours: 352,
      collaborators: 5
    },
    {
      project: "Plataforma RRHH",
      status: "Activo",
      priority: "Baja",
      budget: 38000,
      hours: 240,
      collaborators: 3
    }
  ],
  "2026-07": [
    {
      project: "Clínica AI",
      status: "Activo",
      priority: "Alta",
      budget: 40000,
      hours: 210,
      collaborators: 3
    },
    {
      project: "ERP Cloud",
      status: "Activo",
      priority: "Media",
      budget: 45000,
      hours: 410,
      collaborators: 4
    },
    {
      project: "Sistema de Inventario",
      status: "Activo",
      priority: "Alta",
      budget: 52000,
      hours: 315,
      collaborators: 4
    }
  ]
};

const collaboratorHours = {
  "2026-09": [
    { person: "Daniel Acosta", project: "Clínica AI", role: "DevOps Engineer", hours: 112, tasks: 8 },
    { person: "Ana Torres", project: "Clínica AI", role: "Backend Developer", hours: 126, tasks: 10 },
    { person: "Pedro Díaz", project: "Clínica AI", role: "QA Engineer", hours: 92, tasks: 9 },
    { person: "María Ruiz", project: "Clínica AI", role: "DevOps Engineer", hours: 98, tasks: 7 },
    { person: "Luis Ramos", project: "ERP Cloud", role: "Cloud Engineer", hours: 144, tasks: 11 },
    { person: "Mariana Vega", project: "ERP Cloud", role: "Backend Developer", hours: 128, tasks: 9 },
    { person: "Valeria Ruiz", project: "ERP Cloud", role: "QA Engineer", hours: 88, tasks: 8 },
    { person: "Renato Silva", project: "Sistema de Inventario", role: "Backend Developer", hours: 96, tasks: 9 },
    { person: "Lucía Paredes", project: "Sistema de Inventario", role: "Data Analyst", hours: 84, tasks: 6 },
    { person: "Sofía Torres", project: "Plataforma RRHH", role: "Frontend Developer", hours: 96, tasks: 8 }
  ],
  "2026-08": [
    { person: "Daniel Acosta", project: "Clínica AI", role: "DevOps Engineer", hours: 96, tasks: 7 },
    { person: "Ana Torres", project: "Clínica AI", role: "Backend Developer", hours: 112, tasks: 9 },
    { person: "Luis Ramos", project: "ERP Cloud", role: "Cloud Engineer", hours: 132, tasks: 10 },
    { person: "Mariana Vega", project: "ERP Cloud", role: "Backend Developer", hours: 118, tasks: 8 },
    { person: "Renato Silva", project: "Sistema de Inventario", role: "Backend Developer", hours: 90, tasks: 8 },
    { person: "Lucía Paredes", project: "Sistema de Inventario", role: "Data Analyst", hours: 78, tasks: 6 },
    { person: "Sofía Torres", project: "Plataforma RRHH", role: "Frontend Developer", hours: 104, tasks: 9 }
  ],
  "2026-07": [
    { person: "Ana Torres", project: "Clínica AI", role: "Backend Developer", hours: 96, tasks: 8 },
    { person: "Luis Ramos", project: "ERP Cloud", role: "Cloud Engineer", hours: 124, tasks: 9 },
    { person: "Renato Silva", project: "Sistema de Inventario", role: "Backend Developer", hours: 82, tasks: 7 }
  ]
};

const monthFilter = document.getElementById("monthFilter");
const projectFilter = document.getElementById("projectFilter");
const statusFilter = document.getElementById("statusFilter");
const clearFilters = document.getElementById("clearFilters");

const reportBody = document.getElementById("reportBody");
const collaboratorHoursBody = document.getElementById("collaboratorHoursBody");
const hoursChart = document.getElementById("hoursChart");
const reportInfo = document.getElementById("reportInfo");

const metricProjects = document.getElementById("metricProjects");
const metricBudget = document.getElementById("metricBudget");
const metricHours = document.getElementById("metricHours");
const metricCollaborators = document.getElementById("metricCollaborators");

function money(value) {
  return `S/ ${value.toLocaleString("es-PE")}`;
}

function priorityBadge(priority) {
  const map = {
    Alta: "bg-red-lt text-red",
    Media: "bg-yellow-lt",
    Baja: "bg-green-lt"
  };
  return map[priority] || "bg-secondary-lt";
}

function statusBadge(status) {
  const map = {
    Activo: "bg-green-lt",
    "En espera": "bg-yellow-lt",
    Finalizado: "bg-secondary-lt"
  };
  return map[status] || "bg-secondary-lt";
}

function getFilteredProjects() {
  const month = monthFilter.value;
  const project = projectFilter.value;
  const status = statusFilter.value;

  return (reports[month] || []).filter(row =>
    (project === "all" || row.project === project)
    && (status === "all" || row.status === status)
  );
}

function getFilteredCollaboratorHours() {
  const month = monthFilter.value;
  const selectedProjects = new Set(getFilteredProjects().map(row => row.project));

  return (collaboratorHours[month] || []).filter(row =>
    selectedProjects.has(row.project)
  );
}

function renderProjects(rows) {
  reportBody.innerHTML = rows.length
    ? rows.map(row => `
      <tr>
        <td class="fw-semibold">${row.project}</td>
        <td><span class="badge ${statusBadge(row.status)}">${row.status}</span></td>
        <td><span class="badge ${priorityBadge(row.priority)}">${row.priority}</span></td>
        <td>${money(row.budget)}</td>
        <td class="fw-semibold">${row.hours} h</td>
        <td>${row.collaborators}</td>
        <td class="text-end">
          <a href="${rmViewUrl(
            "rm-detalle-proyecto.html",
            "/rm/proyectos/detalle"
          )}" class="btn btn-outline-secondary btn-sm">Ver proyecto</a>
        </td>
      </tr>
    `).join("")
    : `
      <tr>
        <td colspan="7" class="text-center text-secondary py-5">
          No hay proyectos para los filtros seleccionados.
        </td>
      </tr>
    `;

  reportInfo.textContent = `${rows.length} proyecto${rows.length === 1 ? "" : "s"} incluido${rows.length === 1 ? "" : "s"} en el reporte`;
}

function renderCollaborators(rows) {
  collaboratorHoursBody.innerHTML = rows.length
    ? rows.map(row => `
      <tr>
        <td class="fw-semibold">${row.person}</td>
        <td>${row.project}</td>
        <td>${row.role}</td>
        <td class="fw-semibold">${row.hours} h</td>
        <td>${row.tasks}</td>
      </tr>
    `).join("")
    : `
      <tr>
        <td colspan="5" class="text-center text-secondary py-5">
          No hay horas registradas para los filtros seleccionados.
        </td>
      </tr>
    `;
}

function renderChart(rows) {
  const maxHours = Math.max(1, ...rows.map(row => row.hours));

  hoursChart.innerHTML = rows.length
    ? rows.map(row => {
        const width = Math.round((row.hours / maxHours) * 100);
        return `
          <div class="chart-item">
            <div class="chart-head">
              <span class="fw-semibold">${row.project}</span>
              <span class="text-secondary">${row.hours} h</span>
            </div>
            <div class="chart-track">
              <div class="chart-fill" data-width="${width}"></div>
            </div>
          </div>
        `;
      }).join("")
    : '<div class="text-secondary small">Sin información para mostrar.</div>';

  [...hoursChart.querySelectorAll(".chart-fill")].forEach(fill => {
    fill.style.width = `${fill.dataset.width}%`;
  });
}

function updateMetrics(projectRows, personRows) {
  metricProjects.textContent = projectRows.length;
  metricBudget.textContent = money(
    projectRows.reduce((sum, row) => sum + row.budget, 0)
  );
  metricHours.textContent = `${projectRows.reduce((sum, row) => sum + row.hours, 0)} h`;
  metricCollaborators.textContent = new Set(personRows.map(row => row.person)).size;
}

function render() {
  const projects = getFilteredProjects();
  const people = getFilteredCollaboratorHours();

  renderProjects(projects);
  renderCollaborators(people);
  renderChart(projects);
  updateMetrics(projects, people);
}

[monthFilter, projectFilter, statusFilter].forEach(element => {
  element.addEventListener("change", render);
});

clearFilters.addEventListener("click", () => {
  monthFilter.value = "2026-09";
  projectFilter.value = "all";
  statusFilter.value = "all";
  render();
});

render();
