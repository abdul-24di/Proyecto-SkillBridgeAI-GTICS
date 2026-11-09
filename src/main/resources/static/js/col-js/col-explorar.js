/* Comportamiento de la vista Explorar: acordeones y modales.
   Las pestañas, filtros y paginación ahora se resuelven en el servidor (ColaboradorViewController). */
/* ================= PROYECTOS ================= */

document.querySelectorAll("#projectAccordion [data-toggle-project]").forEach(header => {
    header.addEventListener("click", () => header.closest(".project-accordion-item").classList.toggle("open"));
});


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
});