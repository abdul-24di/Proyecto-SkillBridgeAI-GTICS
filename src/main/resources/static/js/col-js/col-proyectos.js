const misProyectos = [
    { name: "Proyecto A", role: "Backend Developer", description: "Plataforma de gestión interna con dashboards y reportes.", status: "Activo", start: "01 Jun 2026", end: "20 Dic 2026", team: "8 integrantes" },
    { name: "Proyecto B", role: "QA Engineer", description: "Aplicación de ventas en campo con sincronización offline.", status: "Activo", start: "15 Jul 2026", end: "28 Feb 2027", team: "5 integrantes" },
    { name: "Proyecto C", role: "Backend Developer", description: "Migración de infraestructura on-premise a la nube.", status: "Terminado", start: "01 Ene 2026", end: "30 May 2026", team: "6 integrantes" },
    { name: "Proyecto D", role: "Frontend Developer", description: "Portal de autoservicio para clientes con tickets y facturas.", status: "Cancelado", start: "10 Mar 2026", end: "—", team: "4 integrantes" },
    { name: "Proyecto E", role: "Full Stack Developer", description: "Panel analítico para indicadores por departamento.", status: "Activo", start: "05 Ago 2026", end: "15 Feb 2027", team: "5 integrantes" },
    { name: "Proyecto G", role: "Backend Developer", description: "Gateway centralizado de autenticación y observabilidad.", status: "Cancelado", start: "01 Feb 2026", end: "—", team: "4 integrantes" }
];

const grid = document.getElementById("projectGrid");
const searchInput = document.getElementById("searchInput");
const tabs = document.querySelectorAll(".filter-tab");
let currentFilter = "Todos";

function statusClass(s) {
    return { "Activo": "bg-green-lt", "Terminado": "bg-blue-lt", "Cancelado": "bg-secondary-lt" }[s] || "bg-secondary-lt";
}

function render() {
    const q = searchInput.value.trim().toLowerCase();
    const filtered = misProyectos.filter(p => {
        const matchesTab = currentFilter === "Todos"
            || (currentFilter === "Activos" && p.status === "Activo")
            || (currentFilter === "Terminados" && p.status === "Terminado")
            || (currentFilter === "Cancelados" && p.status === "Cancelado");
        const matchesSearch = [p.name, p.description, p.role].join(" ").toLowerCase().includes(q);
        return matchesTab && matchesSearch;
    });

    grid.innerHTML = filtered.length ? filtered.map(p => `
    <div class="col-sm-6 col-xl-4">
      <div class="card project-card">
        <div class="card-body">
          <div class="d-flex justify-content-between align-items-start gap-2">
            <div class="project-title">${p.name}</div>
            <span class="badge ${statusClass(p.status)}">${p.status}</span>
          </div>
          <div class="text-secondary small mt-1">${p.role}</div>
          <div class="project-description mt-2">${p.description}</div>
          <div class="project-meta">
            <div><div class="info-label">Inicio</div><div class="info-value">${p.start}</div></div>
            <div><div class="info-label">Fin</div><div class="info-value">${p.end}</div></div>
            <div><div class="info-label">Equipo</div><div class="info-value">${p.team}</div></div>
          </div>
          <div class="project-actions">
            <a href="col-detalle-proyecto.html" class="btn btn-outline-primary btn-sm">Ver proyecto</a>
          </div>
        </div>
      </div>
    </div>`).join("") : `<div class="col-12"><div class="card"><div class="empty-state">No se encontraron proyectos.</div></div></div>`;
}

tabs.forEach(tab => tab.addEventListener("click", () => {
    tabs.forEach(t => t.classList.remove("active"));
    tab.classList.add("active");
    currentFilter = tab.dataset.filter;
    render();
}));

searchInput.addEventListener("input", render);
render();