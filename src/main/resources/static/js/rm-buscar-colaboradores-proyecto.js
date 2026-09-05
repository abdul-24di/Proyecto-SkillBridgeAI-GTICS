/*
 * SkillBridge AI
 * Buscar colaboradores para proyecto - Resource Manager
 *
 * Datos y lógica temporal de mockup.
 * Al conectar el backend, los candidatos y la paginación deberán
 * obtenerse desde Spring Boot / Spring Data.
 */

const candidates = [
  {
    name: "Luis Ramos",
    initials: "LR",
    role: "Cloud Engineer",
    experienceLevel: "Senior",
    skills: ["AWS", "Docker", "Kubernetes", "Terraform"],
    availabilityHours: 20,
    activeAssignments: 2,
    maxAssignments: 3,
    experience: "7 años",
    verifiedCertificates: 4,
    requiredCovered: 1
  },
  {
    name: "Pedro Díaz",
    initials: "PD",
    role: "Data Engineer",
    experienceLevel: "Senior",
    skills: ["Python", "AWS", "SQL", "Spark"],
    availabilityHours: 28,
    activeAssignments: 1,
    maxAssignments: 3,
    experience: "7 años",
    verifiedCertificates: 4,
    requiredCovered: 1
  },
  {
    name: "Daniel Acosta",
    initials: "DA",
    role: "DevOps Engineer",
    experienceLevel: "Senior",
    skills: ["Kubernetes", "Docker", "AWS", "CI/CD"],
    availabilityHours: 24,
    activeAssignments: 1,
    maxAssignments: 3,
    experience: "4 años",
    verifiedCertificates: 4,
    requiredCovered: 1
  },
  {
    name: "Mariana Vega",
    initials: "MV",
    role: "Backend Developer",
    experienceLevel: "Senior",
    skills: ["Python", "Java", "Spring Boot", "AWS"],
    availabilityHours: 16,
    activeAssignments: 2,
    maxAssignments: 3,
    experience: "6 años",
    verifiedCertificates: 4,
    requiredCovered: 1
  },
  {
    name: "Renato Silva",
    initials: "RS",
    role: "Backend Developer",
    experienceLevel: "Junior",
    skills: ["Java", "Spring Boot", "Docker"],
    availabilityHours: 32,
    activeAssignments: 0,
    maxAssignments: 3,
    experience: "1 año",
    verifiedCertificates: 3,
    requiredCovered: 0
  },
  {
    name: "Andrea León",
    initials: "AL",
    role: "Platform Engineer",
    experienceLevel: "Senior",
    skills: ["Kubernetes", "Python", "AWS", "Docker"],
    availabilityHours: 12,
    activeAssignments: 2,
    maxAssignments: 3,
    experience: "8 años",
    verifiedCertificates: 4,
    requiredCovered: 2
  },
  {
    name: "Mateo Rojas",
    initials: "MR",
    role: "Software Engineer",
    experienceLevel: "Junior",
    skills: ["Python", "Docker", "Java"],
    availabilityHours: 24,
    activeAssignments: 1,
    maxAssignments: 3,
    experience: "4 años",
    verifiedCertificates: 3,
    requiredCovered: 1
  },
  {
    name: "Valeria Ruiz",
    initials: "VR",
    role: "QA Automation Engineer",
    experienceLevel: "Junior",
    skills: ["Python", "Selenium", "Docker"],
    availabilityHours: 20,
    activeAssignments: 1,
    maxAssignments: 3,
    experience: "4 años",
    verifiedCertificates: 3,
    requiredCovered: 1
  }
];

const pageSize = 6;
let currentPage = 1;

const searchInput =
  document.getElementById("searchInput");

const skillFilter =
  document.getElementById("skillFilter");

const availabilityFilter =
  document.getElementById("availabilityFilter");

const loadFilter =
  document.getElementById("loadFilter");

const experienceFilter =
  document.getElementById("experienceFilter");

const clearFilters =
  document.getElementById("clearFilters");

const candidateGrid =
  document.getElementById("candidateGrid");

const pagination =
  document.getElementById("pagination");

const paginationInfo =
  document.getElementById("paginationInfo");


function requiredSkillsBadge(covered) {
  if (covered === 2) {
    return `
      <span class="badge bg-green-lt">
        2/2 habilidades faltantes cubiertas
      </span>
    `;
  }

  if (covered === 1) {
    return `
      <span class="badge bg-yellow-lt">
        1/2 habilidades faltantes cubierta
      </span>
    `;
  }

  return `
    <span class="badge bg-secondary-lt">
      0/2 habilidades faltantes cubiertas
    </span>
  `;
}


function getFilteredCandidates() {
  const search =
    searchInput.value.trim().toLowerCase();

  const skill =
    skillFilter.value;

  const availability =
    availabilityFilter.value === "all"
      ? null
      : Number(availabilityFilter.value);

  const load =
    loadFilter.value === "all"
      ? null
      : Number(loadFilter.value);

  const experience =
    experienceFilter.value;

  return candidates.filter(candidate => {
    const searchable = [
      candidate.name,
      candidate.role,
      candidate.experienceLevel,
      ...candidate.skills
    ].join(" ").toLowerCase();

    const matchesSearch =
      searchable.includes(search);

    const matchesSkill =
      skill === "all" ||
      candidate.skills.includes(skill);

    const matchesAvailability =
      availability === null ||
      candidate.availabilityHours >= availability;

    const matchesLoad =
      load === null ||
      candidate.activeAssignments === load;

    const matchesExperience =
      experience === "all" ||
      candidate.experienceLevel === experience;

    return matchesSearch &&
      matchesSkill &&
      matchesAvailability &&
      matchesLoad &&
      matchesExperience;
  });
}


function renderCandidates() {
  const filtered =
    getFilteredCandidates();

  const totalPages =
    Math.max(1, Math.ceil(filtered.length / pageSize));

  if (currentPage > totalPages) {
    currentPage = totalPages;
  }

  const start =
    (currentPage - 1) * pageSize;

  const visible =
    filtered.slice(start, start + pageSize);

  candidateGrid.innerHTML = visible.length
    ? visible.map(candidate => `
      <div class="col-lg-4 col-md-6">

        <div class="card candidate-card">

          <div class="card-body">

            <div class="d-flex align-items-start gap-3">

              <span class="avatar avatar-lg bg-blue-lt text-blue">
                ${candidate.initials}
              </span>

              <div class="flex-fill">

                <h3 class="candidate-name">
                  ${candidate.name}
                </h3>

                <div class="text-secondary small">
                  ${candidate.role}
                </div>

                <div class="mt-2">
                  <span class="badge bg-secondary-lt">
                    ${candidate.experienceLevel}
                  </span>
                </div>

              </div>

            </div>


            <div class="mt-3">
              ${candidate.skills.map(skill => `
                <span class="badge bg-blue-lt skill-badge">
                  ${skill}
                </span>
              `).join("")}
            </div>


            <div class="required-skills-summary">
              ${requiredSkillsBadge(candidate.requiredCovered)}
            </div>


            <div class="meta-grid">

              <div>
                <div class="meta-label">
                  Disponibilidad
                </div>

                <div class="meta-value text-success">
                  ${candidate.availabilityHours} h / semana
                </div>
              </div>

              <div>
                <div class="meta-label">
                  Carga actual
                </div>

                <div class="meta-value">
                  ${candidate.activeAssignments} / ${candidate.maxAssignments} asignaciones
                </div>
              </div>

              <div>
                <div class="meta-label">
                  Experiencia
                </div>

                <div class="meta-value">
                  ${candidate.experience}
                </div>
              </div>

              <div>
                <div class="meta-label">
                  Nivel
                </div>

                <div class="meta-value">
                  ${candidate.experienceLevel}
                </div>
              </div>

            </div>


            <div class="certification-line">

              <span class="text-secondary small">
                Certificados aprobados:
              </span>

              <span class="fw-semibold">
                ${candidate.verifiedCertificates}
              </span>

            </div>


            <div class="candidate-actions">

              <a href="#"
                 class="btn btn-outline-secondary btn-sm">
                Ver perfil
              </a>

              <a href="#"
                 class="btn btn-primary btn-sm">
                Proponer para el proyecto
              </a>

            </div>

          </div>

        </div>

      </div>
    `).join("")
    : `
      <div class="col-12">
        <div class="card section-card">
          <div class="empty-state">
            No se encontraron colaboradores con los filtros seleccionados.
          </div>
        </div>
      </div>
    `;

  const first =
    filtered.length ? start + 1 : 0;

  const last =
    Math.min(start + pageSize, filtered.length);

  paginationInfo.textContent =
    `Mostrando ${first}-${last} de ${filtered.length} colaboradores`;

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
      renderCandidates();
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
      renderCandidates();
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
      renderCandidates();
    }
  });

  pagination.appendChild(next);
}


[
  searchInput,
  skillFilter,
  availabilityFilter,
  loadFilter,
  experienceFilter
].forEach(element => {
  const eventName =
    element.tagName === "INPUT"
      ? "input"
      : "change";

  element.addEventListener(eventName, () => {
    currentPage = 1;
    renderCandidates();
  });
});


clearFilters.addEventListener("click", () => {
  searchInput.value = "";
  skillFilter.value = "all";
  availabilityFilter.value = "all";
  loadFilter.value = "all";
  experienceFilter.value = "all";

  currentPage = 1;

  renderCandidates();
});


renderCandidates();
