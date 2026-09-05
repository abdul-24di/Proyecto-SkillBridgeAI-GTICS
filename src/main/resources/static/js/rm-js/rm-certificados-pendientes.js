/*
 * SkillBridge AI
 * Certificados pendientes - Resource Manager
 */

const certificates = [
  {
    person: "Carlos Mendoza",
    initials: "CM",
    role: "Backend Developer",
    document: "AWS Certified Developer.pdf",
    type: "Certificación técnica",
    skill: "AWS",
    level: "Intermedio",
    date: "04 Sep 2026"
  },
  {
    person: "Carlos Mendoza",
    initials: "CM",
    role: "Backend Developer",
    document: "Spring Boot Avanzado.pdf",
    type: "Curso especializado",
    skill: "Spring Boot",
    level: "Avanzado",
    date: "04 Sep 2026"
  },
  {
    person: "Ana Torres",
    initials: "AT",
    role: "Backend Developer",
    document: "Oracle Java Professional.pdf",
    type: "Certificación técnica",
    skill: "Java",
    level: "Avanzado",
    date: "03 Sep 2026"
  },
  {
    person: "Luis Ramos",
    initials: "LR",
    role: "Cloud Engineer",
    document: "CKA Kubernetes.pdf",
    type: "Certificación técnica",
    skill: "Kubernetes",
    level: "Avanzado",
    date: "03 Sep 2026"
  },
  {
    person: "Lucía Paredes",
    initials: "LP",
    role: "Data Analyst",
    document: "Python Data Analysis.pdf",
    type: "Curso especializado",
    skill: "Python",
    level: "Intermedio",
    date: "02 Sep 2026"
  },
  {
    person: "Diego Fernández",
    initials: "DF",
    role: "DevOps Engineer",
    document: "Docker Advanced.pdf",
    type: "Diploma",
    skill: "Docker",
    level: "Avanzado",
    date: "01 Sep 2026"
  },
  {
    person: "Renato Silva",
    initials: "RS",
    role: "Backend Developer",
    document: "Java Foundations.pdf",
    type: "Curso especializado",
    skill: "Java",
    level: "Junior",
    date: "31 Ago 2026"
  }
];

const pageSize = 6;
let currentPage = 1;

const certificateBody = document.getElementById("certificateBody");
const pagination = document.getElementById("pagination");
const paginationInfo = document.getElementById("paginationInfo");
const searchInput = document.getElementById("searchInput");
const skillFilter = document.getElementById("skillFilter");
const typeFilter = document.getElementById("typeFilter");
const clearFilters = document.getElementById("clearFilters");

const metricPending = document.getElementById("metricPending");
const metricPeople = document.getElementById("metricPeople");
const metricSkills = document.getElementById("metricSkills");

function updateMetrics() {
  metricPending.textContent = certificates.length;
  metricPeople.textContent = new Set(certificates.map(item => item.person)).size;
  metricSkills.textContent = new Set(certificates.map(item => item.skill)).size;
}

function getFiltered() {
  const search = searchInput.value.trim().toLowerCase();
  const skill = skillFilter.value;
  const type = typeFilter.value;

  return certificates.filter(item => {
    const searchable = [
      item.person,
      item.role,
      item.document,
      item.type,
      item.skill,
      item.level
    ].join(" ").toLowerCase();

    return searchable.includes(search)
      && (skill === "all" || item.skill === skill)
      && (type === "all" || item.type === type);
  });
}

function render() {
  const filtered = getFiltered();
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));

  if (currentPage > totalPages) {
    currentPage = totalPages;
  }

  const start = (currentPage - 1) * pageSize;
  const visible = filtered.slice(start, start + pageSize);

  certificateBody.innerHTML = visible.length
    ? visible.map(item => `
      <tr>
        <td>
          <div class="d-flex align-items-center gap-2">
            <span class="avatar person-avatar bg-blue-lt text-blue">${item.initials}</span>
            <div>
              <div class="fw-semibold">${item.person}</div>
              <div class="small text-secondary">${item.role}</div>
            </div>
          </div>
        </td>
        <td>
          <div class="document-title">${item.document}</div>
          <div class="small text-secondary">${item.type}</div>
        </td>
        <td><span class="badge bg-blue-lt">${item.skill}</span></td>
        <td>${item.level}</td>
        <td>${item.date}</td>
        <td><span class="badge bg-yellow-lt">Pendiente</span></td>
        <td class="text-end">
          <a href="${rmViewUrl(
            "rm-revision-certificado.html",
            "/rm/colaboradores/certificados/revision"
          )}" class="btn btn-primary btn-sm">Revisar</a>
        </td>
      </tr>
    `).join("")
    : `
      <tr>
        <td colspan="7" class="text-center text-secondary py-5">
          No se encontraron certificados con los filtros seleccionados.
        </td>
      </tr>
    `;

  const first = filtered.length ? start + 1 : 0;
  const last = Math.min(start + pageSize, filtered.length);
  paginationInfo.textContent = `Mostrando ${first}-${last} de ${filtered.length} certificados`;

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
      render();
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
      render();
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
      render();
    }
  });
  pagination.appendChild(next);
}

[searchInput, skillFilter, typeFilter].forEach(element => {
  const eventName = element.tagName === "INPUT" ? "input" : "change";
  element.addEventListener(eventName, () => {
    currentPage = 1;
    render();
  });
});

clearFilters.addEventListener("click", () => {
  searchInput.value = "";
  skillFilter.value = "all";
  typeFilter.value = "all";
  currentPage = 1;
  render();
});

updateMetrics();
render();
