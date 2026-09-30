/*
 * SkillBridge AI
 * Talent Matching - Resource Manager
 *
 * Datos y lógica temporal para el mockup.
 * En backend, la explicación y el score vendrán del servicio de IA.
 */

const projects = [
  {
    id: "clinica-ai",
    name: "Clínica AI",
    pm: "Juan Pérez",
    status: "Activo",
    priority: "Alta",
    team: "4 / 6",
    vacancies: 2,
    budget: "S/ 40,000",
    summary: "Plataforma para gestión clínica y analítica hospitalaria.",
    missingSkills: [
      { name: "Kubernetes", level: "Nivel intermedio" },
      { name: "Python", level: "Nivel intermedio" }
    ],
    recommendations: [
      {
        name: "Andrea León",
        initials: "AL",
        role: "Platform Engineer",
        level: "Senior",
        match: 94,
        availabilityHours: 12,
        activeAssignments: 2,
        maxAssignments: 3,
        certificates: 4,
        skills: ["Kubernetes", "Python", "AWS", "Docker"],
        reason: "Cubre las dos habilidades faltantes y cuenta con certificados aprobados relevantes. Su disponibilidad es parcial, por lo que conviene ajustar las horas de la propuesta."
      },
      {
        name: "Daniel Acosta",
        initials: "DA",
        role: "DevOps Engineer",
        level: "Senior",
        match: 88,
        availabilityHours: 24,
        activeAssignments: 1,
        maxAssignments: 3,
        certificates: 4,
        skills: ["Kubernetes", "Docker", "AWS", "CI/CD"],
        reason: "Cubre Kubernetes, tiene disponibilidad alta y una carga de asignaciones baja. Es una opción sólida para infraestructura y despliegue."
      },
      {
        name: "Mariana Vega",
        initials: "MV",
        role: "Backend Developer",
        level: "Senior",
        match: 79,
        availabilityHours: 16,
        activeAssignments: 2,
        maxAssignments: 3,
        certificates: 4,
        skills: ["Python", "Java", "Spring Boot", "AWS"],
        reason: "Cubre Python y aporta experiencia backend alineada con el stack del proyecto. No cubre Kubernetes, por lo que es una recomendación secundaria."
      }
    ]
  },
  {
    id: "erp-cloud",
    name: "ERP Cloud",
    pm: "María Ruiz",
    status: "Activo",
    priority: "Media",
    team: "3 / 5",
    vacancies: 2,
    budget: "S/ 45,000",
    summary: "Migración y modernización de procesos empresariales hacia cloud.",
    missingSkills: [
      { name: "AWS", level: "Nivel avanzado" },
      { name: "Spring Boot", level: "Nivel avanzado" }
    ],
    recommendations: [
      {
        name: "Mariana Vega",
        initials: "MV",
        role: "Backend Developer",
        level: "Senior",
        match: 92,
        availabilityHours: 16,
        activeAssignments: 2,
        maxAssignments: 3,
        certificates: 4,
        skills: ["Python", "Java", "Spring Boot", "AWS"],
        reason: "Cubre AWS y Spring Boot, tiene experiencia backend y mantiene disponibilidad para una nueva propuesta."
      },
      {
        name: "Luis Ramos",
        initials: "LR",
        role: "Cloud Engineer",
        level: "Senior",
        match: 84,
        availabilityHours: 20,
        activeAssignments: 2,
        maxAssignments: 3,
        certificates: 4,
        skills: ["AWS", "Docker", "Kubernetes", "Terraform"],
        reason: "Cubre AWS a nivel fuerte y aporta experiencia cloud. No cubre Spring Boot, pero complementa las necesidades de infraestructura."
      },
      {
        name: "Renato Silva",
        initials: "RS",
        role: "Backend Developer",
        level: "Junior",
        match: 75,
        availabilityHours: 32,
        activeAssignments: 0,
        maxAssignments: 3,
        certificates: 3,
        skills: ["Java", "Spring Boot", "Docker"],
        reason: "Cubre Spring Boot y tiene una disponibilidad muy alta. Su nivel Junior reduce el score frente a perfiles con mayor experiencia."
      }
    ]
  },
  {
    id: "sistema-inventario",
    name: "Sistema de Inventario",
    pm: "Carlos Méndez",
    status: "Activo",
    priority: "Alta",
    team: "5 / 8",
    vacancies: 3,
    budget: "S/ 52,000",
    summary: "Sistema de gestión de inventario con seguimiento en tiempo real y reportes analíticos.",
    missingSkills: [
      { name: "Docker", level: "Nivel avanzado" },
      { name: "React", level: "Nivel intermedio" }
    ],
    recommendations: [
      {
        name: "Daniel Acosta",
        initials: "DA",
        role: "DevOps Engineer",
        level: "Senior",
        match: 89,
        availabilityHours: 24,
        activeAssignments: 1,
        maxAssignments: 3,
        certificates: 4,
        skills: ["Kubernetes", "Docker", "AWS", "CI/CD"],
        reason: "Cubre Docker con experiencia de infraestructura y dispone de capacidad para asumir otra asignación."
      },
      {
        name: "Lucía Paredes",
        initials: "LP",
        role: "Frontend Developer",
        level: "Senior",
        match: 86,
        availabilityHours: 20,
        activeAssignments: 1,
        maxAssignments: 3,
        certificates: 3,
        skills: ["React", "JavaScript", "TypeScript", "UX/UI"],
        reason: "Cubre React y aporta experiencia frontend. Su disponibilidad y carga actual permiten una nueva propuesta."
      },
      {
        name: "Mateo Rojas",
        initials: "MR",
        role: "Software Engineer",
        level: "Junior",
        match: 72,
        availabilityHours: 24,
        activeAssignments: 1,
        maxAssignments: 3,
        certificates: 3,
        skills: ["Python", "Docker", "Java"],
        reason: "Cubre Docker y tiene buena disponibilidad. Su nivel Junior y falta de React lo colocan como alternativa."
      }
    ]
  },
  {
    id: "plataforma-rrhh",
    name: "Plataforma RRHH",
    pm: "Diego Torres",
    status: "Activo",
    priority: "Baja",
    team: "2 / 5",
    vacancies: 3,
    budget: "S/ 38,000",
    summary: "Sistema interno para selección, onboarding y seguimiento de desempeño.",
    missingSkills: [
      { name: "React", level: "Nivel intermedio" },
      { name: "UX/UI", level: "Nivel intermedio" },
      { name: "QA", level: "Nivel intermedio" }
    ],
    recommendations: [
      {
        name: "Lucía Paredes",
        initials: "LP",
        role: "Frontend Developer",
        level: "Senior",
        match: 91,
        availabilityHours: 20,
        activeAssignments: 1,
        maxAssignments: 3,
        certificates: 3,
        skills: ["React", "JavaScript", "TypeScript", "UX/UI"],
        reason: "Cubre React y UX/UI, dos de las tres necesidades principales, con experiencia frontend consolidada."
      },
      {
        name: "Valeria Ruiz",
        initials: "VR",
        role: "QA Automation Engineer",
        level: "Junior",
        match: 81,
        availabilityHours: 20,
        activeAssignments: 1,
        maxAssignments: 3,
        certificates: 3,
        skills: ["QA", "Python", "Selenium", "Docker"],
        reason: "Cubre QA y mantiene disponibilidad para apoyar al proyecto. Es especialmente útil para fortalecer pruebas."
      },
      {
        name: "Renato Silva",
        initials: "RS",
        role: "Backend Developer",
        level: "Junior",
        match: 63,
        availabilityHours: 32,
        activeAssignments: 0,
        maxAssignments: 3,
        certificates: 3,
        skills: ["Java", "Spring Boot", "Docker"],
        reason: "Tiene alta disponibilidad y carga cero, aunque no cubre directamente las habilidades faltantes prioritarias."
      }
    ]
  }
];


const projectList =
  document.getElementById("projectList");

const missingSkillsBox =
  document.getElementById("missingSkillsBox");

const candidateGrid =
  document.getElementById("candidateGrid");

const selectedName =
  document.getElementById("selectedProjectName");

const selectedStatus =
  document.getElementById("selectedProjectStatus");

const selectedPriority =
  document.getElementById("selectedProjectPriority");

const selectedSummary =
  document.getElementById("selectedProjectSummary");

const selectedPM =
  document.getElementById("selectedProjectPM");

const selectedTeam =
  document.getElementById("selectedProjectTeam");

const selectedVacancies =
  document.getElementById("selectedProjectVacancies");

const selectedBudget =
  document.getElementById("selectedProjectBudget");

const selectedMissingCount =
  document.getElementById("selectedProjectMissingCount");

const recommendationProjectLabel =
  document.getElementById("recommendationProjectLabel");


let selectedProjectId =
  projects[0].id;


function priorityBadgeClass(priority) {
  if (priority === "Alta") {
    return "bg-red-lt text-red";
  }

  if (priority === "Media") {
    return "bg-yellow-lt text-yellow";
  }

  return "bg-blue-lt text-blue";
}


function matchBadgeClass(match) {
  if (match >= 85) {
    return "bg-green-lt";
  }

  if (match >= 70) {
    return "bg-yellow-lt";
  }

  return "bg-secondary-lt";
}


function renderProjectList() {
  projectList.innerHTML = projects.map(project => `
    <button type="button"
            class="project-item text-start w-100 ${project.id === selectedProjectId ? "selected" : ""}"
            data-project-id="${project.id}">

      <div class="d-flex justify-content-between align-items-start gap-2">

        <div>
          <div class="project-name">
            ${project.name}
          </div>

          <div class="small text-secondary">
            PM: ${project.pm}
          </div>
        </div>

        <span class="badge bg-green-lt">
          ${project.vacancies}
          ${project.vacancies === 1 ? "vacante" : "vacantes"}
        </span>

      </div>


      <div class="project-meta">

        <div>
          <div class="subtle-label">
            Equipo
          </div>

          <div class="subtle-value">
            ${project.team}
          </div>
        </div>

        <div>
          <div class="subtle-label">
            Prioridad
          </div>

          <div class="mt-1">
            <span class="badge ${priorityBadgeClass(project.priority)}">
              ${project.priority}
            </span>
          </div>
        </div>

      </div>

    </button>
  `).join("");

  document
    .querySelectorAll("[data-project-id]")
    .forEach(item => {
      item.addEventListener("click", () => {
        selectedProjectId =
          item.dataset.projectId;

        renderAll();
      });
    });
}


function renderMissingSkills(project) {
  missingSkillsBox.innerHTML =
    project.missingSkills.map(skill => `
      <div class="missing-skill">

        <div>
          <div class="fw-semibold">
            ${skill.name}
          </div>

          <div class="small text-secondary">
            ${skill.level}
          </div>
        </div>

        <span class="badge bg-yellow-lt">
          Faltante
        </span>

      </div>
    `).join("");
}


function renderSelectedProject(project) {
  // La ruta de navegación la arma el servidor con datos reales (TASK-026); el mock no la modifica.
  selectedName.textContent =
    project.name;

  selectedStatus.textContent =
    project.status;

  selectedPriority.textContent =
    `Prioridad ${project.priority.toLowerCase()}`;

  selectedPriority.className =
    `badge ${priorityBadgeClass(project.priority)}`;

  selectedSummary.textContent =
    project.summary;

  selectedPM.textContent =
    project.pm;

  selectedTeam.textContent =
    project.team;

  selectedVacancies.textContent =
    project.vacancies;

  selectedBudget.textContent =
    project.budget;

  selectedMissingCount.textContent =
    project.missingSkills.length;

  recommendationProjectLabel.textContent =
    project.name;
}


function renderCandidates(project) {
  candidateGrid.innerHTML =
    project.recommendations.map(candidate => `
      <div class="col-lg-4 col-md-6">

        <div class="card candidate-card">

          <div class="card-body">

            <div class="d-flex align-items-start gap-3">

              <span class="avatar avatar-lg bg-blue-lt text-blue">
                ${candidate.initials}
              </span>

              <div class="flex-fill">

                <div class="d-flex justify-content-between align-items-start gap-2">

                  <div>
                    <h3 class="candidate-name">
                      ${candidate.name}
                    </h3>

                    <div class="text-secondary small">
                      ${candidate.role}
                    </div>
                  </div>

                  <span class="badge ${matchBadgeClass(candidate.match)} match-pill">
                    ${candidate.match}% match
                  </span>

                </div>

                <div class="mt-2">
                  <span class="badge bg-secondary-lt">
                    ${candidate.level}
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


            <div class="reason-box mt-3">
              ${candidate.reason}
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
                  Nivel
                </div>

                <div class="meta-value">
                  ${candidate.level}
                </div>
              </div>

              <div>
                <div class="meta-label">
                  Certificados aprobados
                </div>

                <div class="meta-value">
                  ${candidate.certificates}
                </div>
              </div>

            </div>


            <div class="candidate-actions">

              <a href="${rmViewUrl(
                "rm-perfil-colaborador.html",
                "/rm/colaboradores/perfil"
              )}"
                 class="btn btn-outline-secondary btn-sm">
                Ver perfil
              </a>

              <a href="${rmViewUrl(
                "rm-proponer-asignacion.html",
                "/rm/proyectos/proponer-asignacion"
              )}"
                 class="btn btn-primary btn-sm">
                Proponer para el proyecto
              </a>

            </div>

          </div>

        </div>

      </div>
    `).join("");
}


function renderAll() {
  const project =
    projects.find(item => item.id === selectedProjectId);

  renderProjectList();
  renderMissingSkills(project);
  renderSelectedProject(project);
  renderCandidates(project);
}


renderAll();
