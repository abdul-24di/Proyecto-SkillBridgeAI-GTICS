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
const projectItems = Array.from(document.querySelectorAll("#projectAccordion .project-accordion-item"));

function coincideProyectoExplorar(item) {
    const q = (projectSearchInput?.value || "").trim().toLowerCase();
    const estado = projectStatusFilter?.value || "all";
    const postulaciones = projectApplicationsFilter?.value || "all";

    const nombre = (item.dataset.projectName || "").toLowerCase();
    const texto = item.textContent.toLowerCase();

    const coincideTexto = q.length === 0 || nombre.includes(q) || texto.includes(q);
    const coincideEstado = estado === "all" || item.dataset.estado === estado;
    const coincidePostulaciones = postulaciones === "all" || item.dataset.postulaciones === postulaciones;

    return coincideTexto && coincideEstado && coincidePostulaciones;
}

const paginacionProyectosExplorar = projectItems.length ? crearPaginacionTabla({
    filas: projectItems,
    filtroFn: coincideProyectoExplorar,
    paginationEl: document.getElementById("projectPagination"),
    infoEl: document.getElementById("projectPaginationInfo"),
    pageSize: 6,
    etiqueta: "proyecto(s)"
}) : null;

function aplicarFiltrosProyectos() {
    paginacionProyectosExplorar?.reset();
}

projectSearchInput?.addEventListener("input", aplicarFiltrosProyectos);
projectStatusFilter?.addEventListener("change", aplicarFiltrosProyectos);
projectApplicationsFilter?.addEventListener("change", aplicarFiltrosProyectos);


const applyModalEl = document.getElementById("applyModal");

applyModalEl?.addEventListener("show.bs.modal", (event) => {
    const btn = event.relatedTarget;
    if (!btn) return;

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
});



document.querySelectorAll("#applySkillsWrap .apply-skill-chip").forEach(chip => {
    chip.addEventListener("click", () => {
        chip.classList.toggle("active");
        const seleccionadas = [];
        document.querySelectorAll("#applySkillsWrap .apply-skill-chip.active").forEach(c => seleccionadas.push(c.dataset.skill));
        document.getElementById("applyHabilidadesRelevantesInput").value = seleccionadas.join(", ");
    });
});

const applySkillSearchInput = document.getElementById("applySkillSearchInput");
applySkillSearchInput?.addEventListener("input", () => {
    const texto = applySkillSearchInput.value.trim().toLowerCase();
    document.querySelectorAll("#applySkillsWrap .apply-skill-chip").forEach(chip => {
        const coincide = chip.dataset.skill.toLowerCase().includes(texto);
        chip.style.display = coincide ? "" : "none";
    });
});

/* ================= COLABORADORES ================= */

const collabCards = Array.from(document.querySelectorAll("#collabList .collab-card"));

function coincideColaboradorExplorar(card) {
    const texto = (document.getElementById("collabSearch")?.value || "").trim().toLowerCase();
    const nivel = document.getElementById("collabLevelFilter")?.value || "all";

    const contenido = card.textContent.toLowerCase();
    const nivelCard = card.dataset.nivel || "";
    const coincideTexto = texto.length === 0 || contenido.includes(texto);
    const coincideNivel = nivel === "all" || nivelCard === nivel;
    return coincideTexto && coincideNivel;
}

const paginacionColaboradoresExplorar = collabCards.length ? crearPaginacionTabla({
    filas: collabCards,
    filtroFn: coincideColaboradorExplorar,
    paginationEl: document.getElementById("collabPagination"),
    infoEl: document.getElementById("collabPaginationInfo"),
    pageSize: 6,
    etiqueta: "colaborador(es)"
}) : null;

function filtrarListaColaboradores() {
    paginacionColaboradoresExplorar?.reset();
}

["collabSearch", "collabLevelFilter"].forEach(id => {
    const el = document.getElementById(id);
    el?.addEventListener(el.tagName === "INPUT" ? "input" : "change", filtrarListaColaboradores);
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
document.querySelectorAll("#courseAccordion [data-toggle-course]").forEach(header => {
    header.addEventListener("click", () => header.closest(".project-accordion-item").classList.toggle("open"));
});

const courseItems = Array.from(document.querySelectorAll("#courseAccordion .project-accordion-item"));

function coincideCurso(item) {
    const texto = (document.getElementById("courseSearch")?.value || "").trim().toLowerCase();
    const categoria = document.getElementById("courseCategoryFilter")?.value || "all";

    const nombre = (item.dataset.courseName || "").toLowerCase();
    const categoriaCurso = item.dataset.courseCategory || "";
    const contenido = item.textContent.toLowerCase();

    const coincideTexto = texto.length === 0 || nombre.includes(texto) || contenido.includes(texto);
    const coincideCategoria = categoria === "all" || categoriaCurso === categoria;

    return coincideTexto && coincideCategoria;
}

const paginacionCursos = courseItems.length ? crearPaginacionTabla({
    filas: courseItems,
    filtroFn: coincideCurso,
    paginationEl: document.getElementById("coursePagination"),
    infoEl: document.getElementById("coursePaginationInfo"),
    pageSize: 6,
    etiqueta: "curso(s)"
}) : null;

function filtrarCursos() {
    paginacionCursos?.reset();
}

document.getElementById("courseSearch")?.addEventListener("input", filtrarCursos);
document.getElementById("courseCategoryFilter")?.addEventListener("change", filtrarCursos);

const cursoDetalleModalEl = document.getElementById("cursoDetalleModal");

cursoDetalleModalEl?.addEventListener("show.bs.modal", (event) => {
    const btn = event.relatedTarget;
    if (!btn) return;

    document.getElementById("cursoDetalleTitulo").textContent = btn.dataset.courseName;
    document.getElementById("cursoDetalleCategoria").textContent =
        btn.dataset.courseCategoria && btn.dataset.courseCategoria !== "" ? btn.dataset.courseCategoria : "Sin categoría";

    const nombresModalidad = { VIRTUAL: "Virtual", PRESENCIAL: "Presencial", HIBRIDO: "Híbrido" };
    const modalidadBadge = document.getElementById("cursoDetalleModalidad");
    if (btn.dataset.courseModalidad) {
        modalidadBadge.textContent = nombresModalidad[btn.dataset.courseModalidad] || "No especificada";
        modalidadBadge.style.display = "";
    } else {
        modalidadBadge.style.display = "none";
    }

    document.getElementById("cursoDetalleDescripcion").textContent =
        btn.dataset.courseDescripcion && btn.dataset.courseDescripcion !== "" ? btn.dataset.courseDescripcion : "Sin descripción registrada.";

    document.getElementById("cursoDetalleHoras").textContent = btn.dataset.courseHoras + " horas";

    const esAutodidacta = btn.dataset.courseAutodidacta === "true";
    document.getElementById("cursoDetalleRitmo").textContent = esAutodidacta ? "Autodidáctico (a tu ritmo)" : "Con horario fijo";

    const diasWrap = document.getElementById("cursoDetalleDiasWrap");
    if (!esAutodidacta && btn.dataset.courseDias) {
        document.getElementById("cursoDetalleDias").textContent = btn.dataset.courseDias;
        diasWrap.style.display = "";
    } else {
        diasWrap.style.display = "none";
    }

    const fechasEl = document.getElementById("cursoDetalleFechas");
    if (esAutodidacta) {
        fechasEl.textContent = "A tu ritmo, sin fecha fija de inicio ni fin";
    } else if (btn.dataset.courseFechaInicio && btn.dataset.courseFechaFin) {
        fechasEl.textContent = btn.dataset.courseFechaInicio + " – " + btn.dataset.courseFechaFin;
    } else {
        fechasEl.textContent = "Por definir";
    }

    document.getElementById("cursoDetalleCursoId").value = btn.dataset.courseId;

    const puedeSolicitar = btn.dataset.coursePuedeSolicitar === "true";
    const solicitarWrap = document.getElementById("cursoDetalleSolicitarWrap");
    const submitBtn = document.getElementById("cursoDetalleSubmitBtn");
    const estadoWrap = document.getElementById("cursoDetalleEstadoWrap");
    const estadoBadge = document.getElementById("cursoDetalleEstadoBadge");

    if (puedeSolicitar) {
        solicitarWrap.style.display = "";
        submitBtn.style.display = "";
        estadoWrap.style.display = "none";
        document.getElementById("cursoDetalleForm").reset();
    } else {
        solicitarWrap.style.display = "none";
        submitBtn.style.display = "none";
        estadoWrap.style.display = "";
        estadoBadge.textContent = btn.dataset.courseEstadoTexto || "No disponible";
        estadoBadge.className = "badge " + (btn.dataset.courseEstadoClase || "bg-secondary-lt");
    }
});;