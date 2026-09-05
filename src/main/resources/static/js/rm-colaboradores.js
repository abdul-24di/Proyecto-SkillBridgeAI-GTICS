/*
 * SkillBridge AI
 * Colaboradores - Resource Manager
 *
 * Datos y lógica temporal para el mockup.
 * Al conectar el backend, los colaboradores, filtros y paginación
 * deberán obtenerse desde Spring Boot / Spring Data.
 */

const collaborators = [
  {
    name: "Carlos Mendoza",
    initials: "CM",
    role: "Backend Developer",
    level: "Senior",
    skills: ["Java", "Spring Boot", "AWS", "Docker"],
    availabilityHours: 16,
    activeAssignments: 2,
    maxAssignments: 3,
    experience: "6 años",
    approvedCertificates: 4
  },
  {
    name: "Ana Torres",
    initials: "AT",
    role: "Backend Developer",
    level: "Senior",
    skills: ["Java", "Spring Boot", "MySQL"],
    availabilityHours: 20,
    activeAssignments: 1,
    maxAssignments: 3,
    experience: "5 años",
    approvedCertificates: 3
  },
  {
    name: "Luis Ramos",
    initials: "LR",
    role: "Cloud Engineer",
    level: "Senior",
    skills: ["AWS", "Docker", "Kubernetes", "Terraform"],
    availabilityHours: 20,
    activeAssignments: 2,
    maxAssignments: 3,
    experience: "7 años",
    approvedCertificates: 4
  },
  {
    name: "Sofía Torres",
    initials: "ST",
    role: "Frontend Developer",
    level: "Junior",
    skills: ["React", "JavaScript", "TypeScript"],
    availabilityHours: 24,
    activeAssignments: 1,
    maxAssignments: 3,
    experience: "4 años",
    approvedCertificates: 2
  },
  {
    name: "Diego Fernández",
    initials: "DF",
    role: "DevOps Engineer",
    level: "Senior",
    skills: ["Docker", "AWS", "CI/CD", "Linux"],
    availabilityHours: 12,
    activeAssignments: 2,
    maxAssignments: 3,
    experience: "8 años",
    approvedCertificates: 5
  },
  {
    name: "Valeria Ruiz",
    initials: "VR",
    role: "QA Engineer",
    level: "Junior",
    skills: ["Selenium", "Java", "Postman"],
    availabilityHours: 20,
    activeAssignments: 1,
    maxAssignments: 3,
    experience: "4 años",
    approvedCertificates: 2
  },
  {
    name: "Pedro Díaz",
    initials: "PD",
    role: "Data Engineer",
    level: "Senior",
    skills: ["Python", "SQL", "AWS", "Spark"],
    availabilityHours: 0,
    activeAssignments: 3,
    maxAssignments: 3,
    experience: "7 años",
    approvedCertificates: 4
  },
  {
    name: "María López",
    initials: "ML",
    role: "UX/UI Designer",
    level: "Senior",
    skills: ["Figma", "UX Research", "Design Systems"],
    availabilityHours: 32,
    activeAssignments: 0,
    maxAssignments: 3,
    experience: "6 años",
    approvedCertificates: 3
  },
  {
    name: "Renato Silva",
    initials: "RS",
    role: "Backend Developer",
    level: "Junior",
    skills: ["Java", "Spring Boot", "Git"],
    availabilityHours: 32,
    activeAssignments: 0,
    maxAssignments: 3,
    experience: "1 año",
    approvedCertificates: 1
  },
  {
    name: "Camila Herrera",
    initials: "CH",
    role: "Frontend Developer",
    level: "Junior",
    skills: ["React", "JavaScript", "HTML/CSS"],
    availabilityHours: 12,
    activeAssignments: 2,
    maxAssignments: 3,
    experience: "2 años",
    approvedCertificates: 1
  },
  {
    name: "Jorge Salazar",
    initials: "JS",
    role: "Mobile Developer",
    level: "Senior",
    skills: ["Kotlin", "Flutter", "Firebase"],
    availabilityHours: 0,
    activeAssignments: 3,
    maxAssignments: 3,
    experience: "7 años",
    approvedCertificates: 4
  },
  {
    name: "Lucía Paredes",
    initials: "LP",
    role: "Data Analyst",
    level: "Junior",
    skills: ["Python", "Power BI", "SQL"],
    availabilityHours: 20,
    activeAssignments: 1,
    maxAssignments: 3,
    experience: "4 años",
    approvedCertificates: 2
  }
];

const pageSize = 6;
let currentPage = 1;

const searchInput =
  document.getElementById("searchInput");

const availabilityFilter =
  document.getElementById("availabilityFilter");

const loadFilter =
  document.getElementById("loadFilter");

const levelFilter =
  document.getElementById("levelFilter");

const clearFilters =
  document.getElementById("clearFilters");

const collaboratorGrid =
  document.getElementById("collaboratorGrid");

const pagination =
  document.getElementById("pagination");

const paginationInfo =
  document.getElementById("paginationInfo");

const metricTotal =
  document.getElementById("metricTotal");

const metricAvailable =
  document.getElementById("metricAvailable");

const metricFree =
  document.getElementById("metricFree");

const metricMaxLoad =
  document.getElementById("metricMaxLoad");


function availabilityBadgeClass(hours) {
  if (hours >= 16) {
    return "bg-green-lt";
  }

  if (hours > 0) {
    return "bg-yellow-lt";
  }

  return "bg-red-lt";
}


function loadBadgeClass(active, max) {
  if (active >= max) {
    return "bg-red-lt";
  }

  if (active === 0) {
    return "bg-green-lt";
  }

  return "bg-yellow-lt";
}


function updateSummaryMetrics() {
  metricTotal.textContent =
    collaborators.length;

  metricAvailable.textContent =
    collaborators.filter(collaborator =>
      collaborator.availabilityHours >= 16
    ).length;

  metricFree.textContent =
    collaborators.filter(collaborator =>
      collaborator.activeAssignments === 0
    ).length;

  metricMaxLoad.textContent =
    collaborators.filter(collaborator =>
      collaborator.activeAssignments >= collaborator.maxAssignments
    ).length;
}


function getFilteredCollaborators() {
  const search =
    searchInput.value.trim().toLowerCase();

  const availability =
    availabilityFilter.value;

  const load =
    loadFilter.value;

  const level =
    levelFilter.value;

  return collaborators.filter(collaborator => {
    const searchable = [
      collaborator.name,
      collaborator.role,
      collaborator.level,
      ...collaborator.skills
    ].join(" ").toLowerCase();

    const matchesSearch =
      searchable.includes(search);

    let matchesAvailability = true;

    if (availability !== "all") {
      const threshold =
        Number(availability);

      if (threshold === 0) {
        matchesAvailability =
          collaborator.availabilityHours === 0;
      } else {
        matchesAvailability =
          collaborator.availabilityHours >= threshold;
      }
    }

    let matchesLoad = true;

    if (load === "max") {
      matchesLoad =
        collaborator.activeAssignments >= collaborator.maxAssignments;
    } else if (load !== "all") {
      matchesLoad =
        collaborator.activeAssignments === Number(load);
    }

    const matchesLevel =
      level === "all" ||
      collaborator.level === level;

    return matchesSearch &&
      matchesAvailability &&
      matchesLoad &&
      matchesLevel;
  });
}


function renderCollaborators() {
  const filtered =
    getFilteredCollaborators();

  const totalPages =
    Math.max(1, Math.ceil(filtered.length / pageSize));

  if (currentPage > totalPages) {
    currentPage = totalPages;
  }

  const start =
    (currentPage - 1) * pageSize;

  const visible =
    filtered.slice(start, start + pageSize);

  collaboratorGrid.innerHTML = visible.length
    ? visible.map(collaborator => `
      <div class="col-lg-4 col-md-6">

        <div class="card collab-card">

          <div class="card-body">

            <div class="d-flex align-items-start gap-3">

              <span class="avatar avatar-lg bg-blue-lt text-blue">
                ${collaborator.initials}
              </span>

              <div class="flex-fill min-width-0">

                <div class="d-flex justify-content-between gap-2 align-items-start">

                  <div>
                    <h3 class="collab-name">
                      ${collaborator.name}
                    </h3>

                    <div class="text-secondary small">
                      ${collaborator.role}
                    </div>
                  </div>

                  <span class="badge ${availabilityBadgeClass(collaborator.availabilityHours)}">
                    ${collaborator.availabilityHours} h/sem
                  </span>

                </div>

                <div class="mt-2">
                  <span class="badge bg-secondary-lt">
                    ${collaborator.level}
                  </span>
                </div>

              </div>

            </div>


            <div class="mt-3">
              ${collaborator.skills.map(skill => `
                <span class="badge bg-blue-lt skill-badge">
                  ${skill}
                </span>
              `).join("")}
            </div>


            <div class="meta-grid">

              <div>
                <div class="meta-label">
                  Experiencia
                </div>

                <div class="meta-value">
                  ${collaborator.experience}
                </div>
              </div>

              <div>
                <div class="meta-label">
                  Nivel
                </div>

                <div class="meta-value">
                  ${collaborator.level}
                </div>
              </div>

              <div>
                <div class="meta-label">
                  Disponibilidad
                </div>

                <div class="meta-value">
                  ${collaborator.availabilityHours} h / semana
                </div>
              </div>

              <div>
                <div class="meta-label">
                  Carga actual
                </div>

                <div class="meta-value">
                  <span class="badge ${loadBadgeClass(
                    collaborator.activeAssignments,
                    collaborator.maxAssignments
                  )}">
                    ${collaborator.activeAssignments} / ${collaborator.maxAssignments} asignaciones
                  </span>
                </div>
              </div>

            </div>


            <div class="certificate-line">

              <span class="text-secondary small">
                Certificados aprobados:
              </span>

              <span class="fw-semibold">
                ${collaborator.approvedCertificates}
              </span>

            </div>


            <div class="card-actions">

              <a href="#"
                 class="btn btn-outline-secondary btn-sm">
                Ver asignaciones
              </a>

              <a href="#"
                 class="btn btn-primary btn-sm">
                Ver perfil
              </a>

            </div>

          </div>

        </div>

      </div>
    `).join("")
    : `
      <div class="col-12">
        <div class="card">
          <div class="empty-state">
            No se encontraron colaboradores con los filtros seleccionados.
          </div>
        </div>
      </div>
    `;

  const firstItem =
    filtered.length ? start + 1 : 0;

  const lastItem =
    Math.min(start + pageSize, filtered.length);

  paginationInfo.textContent =
    `Mostrando ${firstItem}-${lastItem} de ${filtered.length} colaboradores`;

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
      renderCollaborators();
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
      renderCollaborators();
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
      renderCollaborators();
    }
  });

  pagination.appendChild(next);
}


[
  searchInput,
  availabilityFilter,
  loadFilter,
  levelFilter
].forEach(element => {
  const eventName =
    element.tagName === "INPUT"
      ? "input"
      : "change";

  element.addEventListener(eventName, () => {
    currentPage = 1;
    renderCollaborators();
  });
});


clearFilters.addEventListener("click", () => {
  searchInput.value = "";
  availabilityFilter.value = "all";
  loadFilter.value = "all";
  levelFilter.value = "all";

  currentPage = 1;

  renderCollaborators();
});


updateSummaryMetrics();
renderCollaborators();
