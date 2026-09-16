/* ================= TABS PRINCIPALES ================= */
document.querySelectorAll(".explore-tab").forEach(tab => {
    tab.addEventListener("click", () => {
        document.querySelectorAll(".explore-tab").forEach(t => t.classList.remove("active"));
        document.querySelectorAll(".explore-panel").forEach(p => p.classList.add("d-none"));
        tab.classList.add("active");
        document.getElementById(tab.dataset.panel).classList.remove("d-none");
    });
});

/* ================= PROYECTOS ================= */

document.querySelectorAll("#projectAccordion [data-toggle-project]").forEach(header => {
    header.addEventListener("click", () => header.closest(".project-accordion-item").classList.toggle("open"));
});

const projectSearchInput = document.getElementById("projectSearch");
const projectStatusFilter = document.getElementById("projectStatusFilter");
const projectApplicationsFilter = document.getElementById("projectApplicationsFilter");

function aplicarFiltrosProyectos() {
    const q = (projectSearchInput?.value || "").trim().toLowerCase();
    const estado = projectStatusFilter?.value || "all";
    const postulaciones = projectApplicationsFilter?.value || "all";

    document.querySelectorAll("#projectAccordion .project-accordion-item").forEach(item => {
        const nombre = (item.dataset.projectName || "").toLowerCase();
        const texto = item.textContent.toLowerCase();

        const coincideTexto = q.length === 0 || nombre.includes(q) || texto.includes(q);
        const coincideEstado = estado === "all" || item.dataset.estado === estado;
        const coincidePostulaciones = postulaciones === "all" || item.dataset.postulaciones === postulaciones;

        item.classList.toggle("d-none", !(coincideTexto && coincideEstado && coincidePostulaciones));
    });
}

projectSearchInput?.addEventListener("input", aplicarFiltrosProyectos);
projectStatusFilter?.addEventListener("change", aplicarFiltrosProyectos);
projectApplicationsFilter?.addEventListener("change", aplicarFiltrosProyectos);

document.querySelectorAll("#projectAccordion [data-open-apply]").forEach(btn => {
    btn.addEventListener("click", (e) => {
        e.stopPropagation();
        document.getElementById("applyModalTitle").textContent = "Postulación al " + btn.dataset.projectName;
        document.getElementById("applyProyectoId").value = btn.dataset.projectId;
        document.getElementById("applyMessageInput").value = "";
        document.getElementById("applyHabilidadesRelevantesInput").value = "";

        // Copiamos las opciones de perfil (habilidades con cupo) de ESTE
        // proyecto hacia el <select> del modal — cada proyecto tiene las suyas.
        const item = btn.closest(".project-accordion-item");
        const plantilla = item.querySelector(".perfil-options-template");
        const select = document.getElementById("applyPerfilSelect");
        select.innerHTML = '<option value="" selected disabled>Selecciona un perfil...</option>';
        if (plantilla) {
            select.append(plantilla.content.cloneNode(true));
        }

        // Reiniciamos los chips de habilidades marcadas de una postulación anterior.
        document.querySelectorAll("#applySkillsWrap .apply-skill-chip").forEach(chip => chip.classList.remove("active"));

        const modalEl = document.getElementById("applyModal");
        const Modal = window.bootstrap?.Modal || window.tabler?.bootstrap?.Modal;
        Modal.getOrCreateInstance(modalEl).show();
    });
});

// Al marcar/desmarcar una habilidad propia, actualizamos el campo oculto que
// se envía al servidor con la lista separada por comas.
document.querySelectorAll("#applySkillsWrap .apply-skill-chip").forEach(chip => {
    chip.addEventListener("click", () => {
        chip.classList.toggle("active");
        const seleccionadas = [];
        document.querySelectorAll("#applySkillsWrap .apply-skill-chip.active").forEach(c => seleccionadas.push(c.dataset.skill));
        document.getElementById("applyHabilidadesRelevantesInput").value = seleccionadas.join(", ");
    });
});

/* ================= COLABORADORES ================= */

function filtrarListaColaboradores() {
    const texto = (document.getElementById("collabSearch")?.value || "").trim().toLowerCase();
    const nivel = document.getElementById("collabLevelFilter")?.value || "all";

    document.querySelectorAll("#collabList .collab-card").forEach(card => {
        const contenido = card.textContent.toLowerCase();
        const nivelCard = card.dataset.nivel || "";
        const coincideTexto = texto.length === 0 || contenido.includes(texto);
        const coincideNivel = nivel === "all" || nivelCard === nivel;
        card.classList.toggle("d-none", !(coincideTexto && coincideNivel));
    });
}

["collabSearch", "collabLevelFilter"].forEach(id => {
    const el = document.getElementById(id);
    el?.addEventListener(el.tagName === "INPUT" ? "input" : "change", filtrarListaColaboradores);
});

document.querySelectorAll("#collabList [data-view-collab]").forEach(btn => {
    btn.addEventListener("click", () => mostrarPerfilColaborador(btn.dataset.collabId));
});

function mostrarPerfilColaborador(colaboradorId) {
    document.querySelectorAll("#collabProfileDetail .collab-detail-card").forEach(card => {
        card.classList.toggle("d-none", card.dataset.collabDetail !== colaboradorId);
    });
    document.getElementById("collabListView").classList.add("hide");
    document.getElementById("collabProfileDetail").classList.add("show");
}

document.getElementById("backToCollabList")?.addEventListener("click", () => {
    document.getElementById("collabProfileDetail").classList.remove("show");
    document.getElementById("collabListView").classList.remove("hide");
});

/* ================= CURSOS ================= */
const courses = [
    { id: 1, nombre: "Spring Security Avanzado", categoria: "Técnico", horas: 12, descripcion: "Aprende a proteger APIs REST con autenticación y autorización robustas.", estado: null },
    { id: 2, nombre: "Comunicación Efectiva en Equipos Ágiles", categoria: "Habilidades blandas", horas: 6, descripcion: "Mejora la comunicación dentro de equipos multidisciplinarios y ágiles.", estado: "Pendiente" },
    { id: 3, nombre: "AWS Certified Developer — Preparación", categoria: "Certificación", horas: 20, descripcion: "Preparación guiada para la certificación AWS enfocada en desarrolladores.", estado: "Aprobada" },
    { id: 4, nombre: "React Avanzado", categoria: "Técnico", horas: 10, descripcion: "Patrones avanzados, rendimiento y testing en aplicaciones React.", estado: "Rechazada" }
];

function courseStateMarkup(course) {
    if (course.estado === "Pendiente") return `<span class="badge bg-yellow-lt">Solicitud pendiente</span>`;
    if (course.estado === "Aprobada") return `<span class="badge bg-green-lt">Inscrito</span>`;
    if (course.estado === "Rechazada") return `
        <span class="badge bg-red-lt text-red mb-2 d-block">Solicitud rechazada</span>
        <button type="button" class="btn btn-outline-primary btn-sm" data-request-course="${course.id}">Volver a solicitar</button>`;
    return `<button type="button" class="btn btn-primary btn-sm" data-request-course="${course.id}">Solicitar inscripción</button>`;
}

function renderCourses() {
    const q = (document.getElementById("courseSearch")?.value || "").trim().toLowerCase();
    const cat = document.getElementById("courseCategoryFilter")?.value || "all";

    const filtered = courses.filter(c => {
        const matchesSearch = (c.nombre + " " + c.descripcion).toLowerCase().includes(q);
        const matchesCat = cat === "all" || c.categoria === cat;
        return matchesSearch && matchesCat;
    });

    document.getElementById("courseList").innerHTML = filtered.length ? filtered.map(c => `
        <div class="course-card">
            <div>
                <div class="course-card-title">${c.nombre}</div>
                <div class="course-card-meta"><span class="badge bg-blue-lt me-2">${c.categoria}</span>${c.horas} horas</div>
                <div class="course-card-desc">${c.descripcion}</div>
            </div>
            <div class="course-card-action">${courseStateMarkup(c)}</div>
        </div>`).join("") : `<div class="card"><div class="empty-state">No se encontraron cursos.</div></div>`;

    document.getElementById("courseList").querySelectorAll("[data-request-course]").forEach(btn => {
        btn.addEventListener("click", () => {
            const course = courses.find(x => x.id === Number(btn.dataset.requestCourse));
            course.estado = "Pendiente";
            renderCourses();
        });
    });
}

["courseSearch", "courseCategoryFilter"].forEach(id => {
    const el = document.getElementById(id);
    el?.addEventListener(el.tagName === "INPUT" ? "input" : "change", renderCourses);
});

renderCourses();