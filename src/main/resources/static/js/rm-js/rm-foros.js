/*
 * SkillBridge AI
 * Foros - Resource Manager
 */

const forums = [
  { project:"Clínica AI", pm:"Juan Pérez", status:"Activo", posts:18, participants:6, lastActivity:"05 Sep 2026 · 09:40", daysAgo:0, latestTopic:"Despliegue de servicios con Kubernetes", tags:["Kubernetes","DevOps"] },
  { project:"ERP Cloud", pm:"María Ruiz", status:"Activo", posts:24, participants:5, lastActivity:"04 Sep 2026 · 16:15", daysAgo:1, latestTopic:"Criterios para migración del módulo financiero", tags:["Cloud","Arquitectura"] },
  { project:"Sistema de Inventario", pm:"Carlos Méndez", status:"Activo", posts:13, participants:8, lastActivity:"03 Sep 2026 · 12:05", daysAgo:2, latestTopic:"Sincronización de stock entre sedes", tags:["Backend","Integraciones"] },
  { project:"Plataforma RRHH", pm:"Diego Torres", status:"Activo", posts:9, participants:5, lastActivity:"01 Sep 2026 · 18:22", daysAgo:4, latestTopic:"Validación de flujo de vacaciones", tags:["Procesos","UX"] },
  { project:"DataHub", pm:"Ana Salazar", status:"En espera", posts:15, participants:4, lastActivity:"25 Ago 2026 · 11:30", daysAgo:11, latestTopic:"Pendientes para la siguiente iteración", tags:["Datos","ETL"] },
  { project:"Portal de Clientes", pm:"Marco Ruiz", status:"Finalizado", posts:31, participants:7, lastActivity:"12 Ago 2026 · 14:10", daysAgo:24, latestTopic:"Cierre del proyecto y lecciones aprendidas", tags:["Cierre","Retrospectiva"] }
];

const pageSize = 4;
let currentPage = 1;

const forumSearch = document.getElementById("forumSearch");
const forumStatusFilter = document.getElementById("forumStatusFilter");
const forumActivityFilter = document.getElementById("forumActivityFilter");
const clearForumFilters = document.getElementById("clearForumFilters");
const forumList = document.getElementById("forumList");
const forumPagination = document.getElementById("forumPagination");
const forumPaginationInfo = document.getElementById("forumPaginationInfo");

const forumMetricTotal = document.getElementById("forumMetricTotal");
const forumMetricActive = document.getElementById("forumMetricActive");
const forumMetricPosts = document.getElementById("forumMetricPosts");

function statusBadge(status) {
  const map = {
    "Activo": "bg-green-lt",
    "En espera": "bg-yellow-lt",
    "Finalizado": "bg-secondary-lt"
  };
  return map[status] || "bg-secondary-lt";
}

function updateMetrics() {
  forumMetricTotal.textContent = forums.length;
  forumMetricActive.textContent = forums.filter(item => item.status === "Activo").length;
  forumMetricPosts.textContent = forums.reduce((total, item) => total + item.posts, 0);
}

function filteredForums() {
  const search = forumSearch.value.trim().toLowerCase();
  const status = forumStatusFilter.value;
  const activity = forumActivityFilter.value;

  return forums.filter(item => {
    const searchable =
      `${item.project} ${item.pm} ${item.latestTopic} ${item.tags.join(" ")}`.toLowerCase();

    let matchesActivity = true;
    if (activity === "recent") matchesActivity = item.daysAgo <= 7;
    if (activity === "older") matchesActivity = item.daysAgo > 7;

    return searchable.includes(search)
      && (status === "all" || item.status === status)
      && matchesActivity;
  });
}

function renderForums() {
  const filtered = filteredForums();
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  if (currentPage > totalPages) currentPage = totalPages;

  const start = (currentPage - 1) * pageSize;
  const visible = filtered.slice(start, start + pageSize);

  forumList.innerHTML = visible.length
    ? visible.map(item => `
      <div class="forum-row">
        <div>
          <div class="d-flex align-items-center gap-2 flex-wrap">
            <div class="forum-project">${item.project}</div>
            <span class="badge ${statusBadge(item.status)}">${item.status}</span>
          </div>
          <div class="forum-topic">${item.latestTopic}</div>
          <div class="forum-tags">
            ${item.tags.map(tag => `<span class="badge bg-blue-lt">${tag}</span>`).join("")}
          </div>
        </div>

        <div>
          <div class="forum-meta-label">Project Manager</div>
          <div class="forum-meta-value">${item.pm}</div>
        </div>

        <div>
          <div class="forum-meta-label">Actividad</div>
          <div class="forum-meta-value">${item.posts} publicaciones</div>
          <div class="small text-secondary">${item.participants} participantes</div>
        </div>

        <div>
          <div class="forum-meta-label">Última actividad</div>
          <div class="forum-meta-value">${item.lastActivity}</div>
        </div>

        <div class="forum-row-action text-end">
          <a href="${rmViewUrl(
            "rm-foro-detalle.html",
            "/rm/foros/detalle"
          )}" class="btn btn-outline-primary btn-sm">Ver foro</a>
        </div>
      </div>
    `).join("")
    : `<div class="forum-empty">No se encontraron foros con los filtros seleccionados.</div>`;

  const first = filtered.length ? start + 1 : 0;
  const last = Math.min(start + pageSize, filtered.length);
  forumPaginationInfo.textContent = `Mostrando ${first}-${last} de ${filtered.length} foros`;

  renderPagination(totalPages);
}

function renderPagination(totalPages) {
  forumPagination.innerHTML = "";

  const previous = document.createElement("li");
  previous.className = `page-item ${currentPage === 1 ? "disabled" : ""}`;
  previous.innerHTML = '<a class="page-link" href="#">Anterior</a>';
  previous.addEventListener("click", event => {
    event.preventDefault();
    if (currentPage > 1) {
      currentPage--;
      renderForums();
    }
  });
  forumPagination.appendChild(previous);

  for (let page = 1; page <= totalPages; page++) {
    const item = document.createElement("li");
    item.className = `page-item ${page === currentPage ? "active" : ""}`;
    item.innerHTML = `<a class="page-link" href="#">${page}</a>`;
    item.addEventListener("click", event => {
      event.preventDefault();
      currentPage = page;
      renderForums();
    });
    forumPagination.appendChild(item);
  }

  const next = document.createElement("li");
  next.className = `page-item ${currentPage === totalPages ? "disabled" : ""}`;
  next.innerHTML = '<a class="page-link" href="#">Siguiente</a>';
  next.addEventListener("click", event => {
    event.preventDefault();
    if (currentPage < totalPages) {
      currentPage++;
      renderForums();
    }
  });
  forumPagination.appendChild(next);
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

updateMetrics();
renderForums();
