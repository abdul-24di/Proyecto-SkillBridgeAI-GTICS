/*
 * SkillBridge AI
 * Historial de validaciones - Resource Manager
 *
 * Datos y lógica temporal para el mockup.
 * En backend, el historial se obtendrá desde la entidad de certificados
 * y las acciones de revisión realizadas por el RM.
 */

const validations = [
  {
    document: "Oracle Java Professional.pdf",
    type: "Certificación técnica",
    skill: "Java",
    status: "Aprobado",
    reviewer: "Abraham Ramirez",
    date: "19 Ago 2026 · 10:42",
    note: "Documento válido. La habilidad Java queda validada en el perfil."
  },
  {
    document: "Docker Foundations.pdf",
    type: "Curso especializado",
    skill: "Docker",
    status: "Aprobado",
    reviewer: "Abraham Ramirez",
    date: "16 Ago 2026 · 15:10",
    note: "Acreditación aprobada y asociada a la habilidad Docker."
  },
  {
    document: "AWS Cloud Practitioner.pdf",
    type: "Certificación técnica",
    skill: "AWS",
    status: "Aprobado",
    reviewer: "Abraham Ramirez",
    date: "12 Ago 2026 · 09:35",
    note: "Certificación vigente y coherente con la habilidad declarada."
  },
  {
    document: "Spring Boot Enterprise.pdf",
    type: "Curso especializado",
    skill: "Spring Boot",
    status: "Aprobado",
    reviewer: "Abraham Ramirez",
    date: "08 Ago 2026 · 16:28",
    note: "Contenido compatible con el nivel declarado para Spring Boot."
  },
  {
    document: "Constancia Kubernetes Basics.pdf",
    type: "Curso introductorio",
    skill: "Kubernetes",
    status: "Rechazado",
    reviewer: "Abraham Ramirez",
    date: "05 Ago 2026 · 11:14",
    note: "No se puede verificar claramente la entidad emisora. El certificado permanece registrado como rechazado."
  },
  {
    document: "Diploma Java Avanzado.pdf",
    type: "Diploma",
    skill: "Java",
    status: "Rechazado",
    reviewer: "Abraham Ramirez",
    date: "01 Ago 2026 · 14:02",
    note: "La imagen es ilegible y no permite comprobar los datos del documento."
  },
  {
    document: "AWS Certified Developer.pdf",
    type: "Certificación técnica",
    skill: "AWS",
    status: "Pendiente",
    reviewer: "—",
    date: "—",
    note: "Pendiente de revisión por el Resource Manager."
  },
  {
    document: "Diploma Spring Boot Avanzado.pdf",
    type: "Curso especializado",
    skill: "Spring Boot",
    status: "Pendiente",
    reviewer: "—",
    date: "—",
    note: "Pendiente de revisión por el Resource Manager."
  }
];

const pageSize = 6;
let currentPage = 1;

const historyBody =
  document.getElementById("historyBody");

const pagination =
  document.getElementById("pagination");

const paginationInfo =
  document.getElementById("paginationInfo");

const searchInput =
  document.getElementById("searchInput");

const statusFilter =
  document.getElementById("statusFilter");

const skillFilter =
  document.getElementById("skillFilter");

const clearFilters =
  document.getElementById("clearFilters");

const metricApproved =
  document.getElementById("metricApproved");

const metricRejected =
  document.getElementById("metricRejected");

const metricPending =
  document.getElementById("metricPending");

const metricTotal =
  document.getElementById("metricTotal");


function statusBadgeClass(status) {
  if (status === "Aprobado") {
    return "bg-green-lt";
  }

  if (status === "Rechazado") {
    return "bg-red-lt";
  }

  return "bg-yellow-lt";
}


function actionButton(status) {
  if (status === "Pendiente") {
    return `
      <a href="#"
         class="btn btn-primary btn-sm">
        Revisar
      </a>
    `;
  }

  if (status === "Rechazado") {
    return `
      <a href="#"
         class="btn btn-outline-secondary btn-sm">
        Ver motivo
      </a>
    `;
  }

  return `
    <a href="#"
       class="btn btn-outline-secondary btn-sm">
      Ver
    </a>
  `;
}


function updateMetrics() {
  metricApproved.textContent =
    validations.filter(item =>
      item.status === "Aprobado"
    ).length;

  metricRejected.textContent =
    validations.filter(item =>
      item.status === "Rechazado"
    ).length;

  metricPending.textContent =
    validations.filter(item =>
      item.status === "Pendiente"
    ).length;

  metricTotal.textContent =
    validations.length;
}


function getFilteredRows() {
  const search =
    searchInput.value.trim().toLowerCase();

  const status =
    statusFilter.value;

  const skill =
    skillFilter.value;

  return validations.filter(validation => {
    const searchable = [
      validation.document,
      validation.type,
      validation.skill,
      validation.status,
      validation.note
    ].join(" ").toLowerCase();

    const matchesSearch =
      searchable.includes(search);

    const matchesStatus =
      status === "all" ||
      validation.status === status;

    const matchesSkill =
      skill === "all" ||
      validation.skill === skill;

    return matchesSearch &&
      matchesStatus &&
      matchesSkill;
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

  historyBody.innerHTML = visible.length
    ? visible.map(validation => `
      <tr>

        <td>
          <div class="doc-title">
            ${validation.document}
          </div>

          <div class="small text-secondary">
            ${validation.type}
          </div>
        </td>

        <td>
          <span class="badge bg-blue-lt">
            ${validation.skill}
          </span>
        </td>

        <td>
          <span class="badge ${statusBadgeClass(validation.status)}">
            ${validation.status}
          </span>
        </td>

        <td>
          ${validation.reviewer}
        </td>

        <td>
          ${validation.date}
        </td>

        <td>
          ${
            validation.status === "Rechazado"
              ? `<div class="reason-box">${validation.note}</div>`
              : `<div class="observation">${validation.note}</div>`
          }
        </td>

        <td class="text-end">
          ${actionButton(validation.status)}
        </td>

      </tr>
    `).join("")
    : `
      <tr>
        <td colspan="7"
            class="text-center text-secondary py-5">
          No se encontraron validaciones con los filtros seleccionados.
        </td>
      </tr>
    `;

  const first =
    filtered.length ? start + 1 : 0;

  const last =
    Math.min(start + pageSize, filtered.length);

  paginationInfo.textContent =
    `Mostrando ${first}-${last} de ${filtered.length} documentos`;

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


[
  searchInput,
  statusFilter,
  skillFilter
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
  statusFilter.value = "all";
  skillFilter.value = "all";
  currentPage = 1;

  renderRows();
});


updateMetrics();
renderRows();
