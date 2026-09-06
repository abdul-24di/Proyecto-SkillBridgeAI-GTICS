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
const exploreProjects = [
    {
        id: 1, name: "Proyecto X", estado: "En planificación", estadoDot: "bg-yellow",
        postulacionesAbiertas: true,
        description: "Plataforma inteligente para la gestión de colaboradores.",
        pm: "Jeffie Lewzey", equipoActual: 6, equipoTotal: 10,
        perfiles: [
            { nombre: "Backend Developer", vacantes: 1, tech: "Java / Spring Boot" },
            { nombre: "Frontend Developer", vacantes: 2, tech: "React" },
            { nombre: "QA Tester", vacantes: 1, tech: "" }
        ],
        tecnologias: ["Java", "Spring Boot", "MySQL", "AWS"],
        fechaInicio: "01/09/2026", fechaFin: "20/02/2027",
        yaPostulado: false
    },
    {
        id: 2, name: "Proyecto Y", estado: "Activo", estadoDot: "bg-green",
        postulacionesAbiertas: false,
        description: "Rediseño del portal de autoservicio para clientes.",
        pm: "María López", equipoActual: 5, equipoTotal: 5,
        perfiles: [{ nombre: "UX/UI Designer", vacantes: 0, tech: "Figma" }],
        tecnologias: ["React", "Figma"], fechaInicio: "10/03/2026", fechaFin: "—",
        yaPostulado: false
    },
    {
        id: 3, name: "Proyecto Z", estado: "En planificación", estadoDot: "bg-yellow",
        postulacionesAbiertas: true,
        description: "Nuevo módulo de reportes financieros para el área de contabilidad.",
        pm: "Carlos Mendoza", equipoActual: 2, equipoTotal: 6,
        perfiles: [
            { nombre: "Backend Developer", vacantes: 2, tech: "Java / MySQL" },
            { nombre: "QA Tester", vacantes: 1, tech: "" }
        ],
        tecnologias: ["Java", "MySQL", "Docker"], fechaInicio: "01/10/2026", fechaFin: "30/04/2027",
        yaPostulado: true
    }
];

const misHabilidades = ["Java", "Spring Boot", "MySQL"];

function renderProjectAccordion() {
    const q = (document.getElementById("projectSearch")?.value || "").trim().toLowerCase();
    const estado = document.getElementById("projectStatusFilter")?.value || "all";
    const postulaciones = document.getElementById("projectApplicationsFilter")?.value || "all";

    const filtered = exploreProjects.filter(p => {
        const matchesSearch = (p.name + " " + p.description).toLowerCase().includes(q);
        const matchesEstado = estado === "all" || p.estado === estado;
        const matchesPost = postulaciones === "all"
            || (postulaciones === "Abiertas" && p.postulacionesAbiertas)
            || (postulaciones === "Cerradas" && !p.postulacionesAbiertas);
        return matchesSearch && matchesEstado && matchesPost;
    });

    const container = document.getElementById("projectAccordion");
    container.innerHTML = filtered.length ? filtered.map(p => `
        <div class="project-accordion-item" data-project-id="${p.id}">
            <div class="project-accordion-header" data-toggle-project="${p.id}">
                <div>
                    <div class="project-accordion-title">
                        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z"/></svg>
                        ${p.name}
                    </div>
                    <div class="text-secondary small mt-1">
                        <span class="status-inline-dot ${p.estadoDot}"></span>${p.estado}
                        &nbsp;·&nbsp;
                        <span class="status-inline-dot ${p.postulacionesAbiertas ? "bg-green" : "bg-secondary"}"></span>${p.postulacionesAbiertas ? "Postulaciones abiertas" : "Postulaciones cerradas"}
                    </div>
                </div>
                <svg class="project-accordion-chevron" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 6l6 6-6 6"/></svg>
            </div>
            <div class="project-accordion-body">
                <p class="text-secondary small">${p.description}</p>
                <div class="profile-list-line"><strong>PM:</strong> ${p.pm}</div>
                <div class="profile-list-line"><strong>Equipo:</strong> ${p.equipoActual} / ${p.equipoTotal} colaboradores</div>
                <div class="info-label mt-3 mb-1">Perfiles que busca</div>
                ${p.perfiles.map(pf => `<div class="profile-list-line">• ${pf.nombre}${pf.vacantes ? ` — ${pf.vacantes} vacante${pf.vacantes > 1 ? "s" : ""}` : " — sin vacantes"}${pf.tech ? ` (${pf.tech})` : ""}</div>`).join("")}
                <div class="info-label mt-3 mb-1">Tecnologías</div>
                <div>${p.tecnologias.map(t => `<span class="badge bg-blue-lt me-1 mb-1">${t}</span>`).join("")}</div>
                <div class="info-grid mt-3">
                    <div><div class="info-label">Fecha de inicio</div><div class="info-value">${p.fechaInicio}</div></div>
                    <div><div class="info-label">Fin estimado</div><div class="info-value">${p.fechaFin}</div></div>
                </div>
                <div class="action-bar mt-3 mb-0">
                    ${p.yaPostulado
        ? `<span class="badge bg-blue-lt">Ya te postulaste a este proyecto</span>`
        : p.postulacionesAbiertas
            ? `<button type="button" class="btn btn-primary btn-sm" data-open-apply="${p.id}">Postularme</button>`
            : `<span class="badge bg-secondary-lt">Postulaciones cerradas</span>`}
                </div>
            </div>
        </div>`).join("") : `<div class="card"><div class="empty-state">No se encontraron proyectos.</div></div>`;

    container.querySelectorAll("[data-toggle-project]").forEach(header => {
        header.addEventListener("click", () => header.closest(".project-accordion-item").classList.toggle("open"));
    });

    container.querySelectorAll("[data-open-apply]").forEach(btn => {
        btn.addEventListener("click", (e) => {
            e.stopPropagation();
            openApplyModal(Number(btn.dataset.openApply));
        });
    });
}

["projectSearch", "projectStatusFilter", "projectApplicationsFilter"].forEach(id => {
    const el = document.getElementById(id);
    el?.addEventListener(el.tagName === "INPUT" ? "input" : "change", renderProjectAccordion);
});

renderProjectAccordion();

function openApplyModal(projectId) {
    const p = exploreProjects.find(x => x.id === projectId);
    if (!p) return;

    document.getElementById("applyModalTitle").textContent = `Postulación al ${p.name}`;

    document.getElementById("applyPerfilSelect").innerHTML = p.perfiles.map(pf => `<option>${pf.nombre}</option>`).join("");

    const habilidadesWrap = document.getElementById("applySkillsWrap");
    habilidadesWrap.innerHTML = misHabilidades.map(h => `<button type="button" class="btn btn-outline-secondary btn-sm apply-skill-chip" data-skill="${h}">${h}</button>`).join("");
    habilidadesWrap.querySelectorAll(".apply-skill-chip").forEach(chip => {
        chip.addEventListener("click", () => chip.classList.toggle("active"));
    });

    document.getElementById("applyMessageInput").value = "";

    const modalEl = document.getElementById("applyModal");
    const Modal = window.bootstrap?.Modal || window.tabler?.bootstrap?.Modal;
    Modal.getOrCreateInstance(modalEl).show();

    document.getElementById("applySubmitBtn").onclick = () => {
        p.yaPostulado = true;
        renderProjectAccordion();
        Modal.getOrCreateInstance(modalEl).hide();
    };
}

/* ================= COLABORADORES ================= */
const exploreCollaborators = [
    {
        id: "jeffie", name: "Jeffie Lewzey", role: "Full Stack Senior", nivel: "Avanzado",
        skills: ["Java", "Spring Boot", "React", "MySQL"], years: 5, availability: "Disponible", availabilityDot: "bg-green",
        bio: "Persona especializada en desarrollo full stack y liderazgo técnico de equipos.",
        experience: ["Senior Full Stack Developer: Desarrollo de aplicaciones web y liderazgo técnico.", "Full Stack Developer: Desarrollo e integración de aplicaciones empresariales."],
        skillLevels: [["Java", "Avanzado"], ["Spring Boot", "Avanzado"], ["MySQL", "Intermedio"], ["Git", "Básico"]],
        education: "Ingeniería de Software en Universidad X",
        highlights: ["Proyecto X: Java · Spring Boot · MySQL"]
    },
    {
        id: "maria", name: "María López", role: "UX/UI Designer", nivel: "Intermedio",
        skills: ["Figma", "UX Research", "Prototyping"], years: 3, availability: "Disponibilidad limitada", availabilityDot: "bg-yellow",
        bio: "Diseñadora UX/UI enfocada en investigación de usuarios y prototipado rápido.",
        experience: ["UX/UI Designer — SkillBridge AI: Diseño de flujos y research con usuarios."],
        skillLevels: [["Figma", "Avanzado"], ["UX Research", "Intermedio"], ["Prototyping", "Intermedio"]],
        education: "Diseño Gráfico en Universidad Z",
        highlights: ["Proyecto Y: Figma · Design System"]
    },
    {
        id: "mallory", name: "Mallory Hulme", role: "Frontend Developer", nivel: "Intermedio",
        skills: ["React", "JavaScript", "CSS"], years: 2, availability: "Disponible", availabilityDot: "bg-green",
        bio: "Desarrolladora frontend enfocada en interfaces accesibles y de alto rendimiento.",
        experience: ["Frontend Developer — SkillBridge AI: Construcción de interfaces con React."],
        skillLevels: [["React", "Avanzado"], ["JavaScript", "Avanzado"], ["CSS", "Intermedio"]],
        education: "Ingeniería de Sistemas en Universidad Y",
        highlights: ["Proyecto A: React · TypeScript"]
    },
    {
        id: "dunn", name: "Dunn Slane", role: "UI/UX Designer", nivel: "Avanzado",
        skills: ["Figma", "Design System"], years: 4, availability: "No disponible", availabilityDot: "bg-secondary",
        bio: "Diseñador UI/UX enfocado en investigación de usuarios y sistemas de diseño.",
        experience: ["UI/UX Designer — SkillBridge AI: Diseño de flujos y sistema de componentes."],
        skillLevels: [["Figma", "Avanzado"], ["Investigación UX", "Intermedio"]],
        education: "Diseño Gráfico en Universidad Z",
        highlights: ["Proyecto A: Figma · Design System"]
    }
];

function renderCollaboratorList() {
    const q = (document.getElementById("collabSearch")?.value || "").trim().toLowerCase();
    const nivel = document.getElementById("collabLevelFilter")?.value || "all";

    const filtered = exploreCollaborators.filter(c => {
        const matchesSearch = (c.name + " " + c.role + " " + c.skills.join(" ")).toLowerCase().includes(q);
        const matchesNivel = nivel === "all" || c.nivel === nivel;
        return matchesSearch && matchesNivel;
    });

    document.getElementById("collabList").innerHTML = filtered.length ? filtered.map(c => `
        <div class="collab-card">
            <div class="collab-card-name">${c.name}</div>
            <div class="collab-card-role">${c.role}</div>
            <div class="collab-card-meta">
                <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>
                ${c.skills.join(" · ")}
            </div>
            <div class="collab-card-meta">
                <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z"/></svg>
                ${c.years} años de experiencia
            </div>
            <div class="collab-card-meta"><span class="status-inline-dot ${c.availabilityDot}"></span>${c.availability}</div>
            <button type="button" class="btn btn-outline-primary btn-sm mt-2" data-view-collab="${c.id}">Ver perfil</button>
        </div>`).join("") : `<div class="card"><div class="empty-state">No se encontraron colaboradores.</div></div>`;

    document.getElementById("collabList").querySelectorAll("[data-view-collab]").forEach(btn => {
        btn.addEventListener("click", () => showCollabProfile(btn.dataset.viewCollab));
    });
}

["collabSearch", "collabLevelFilter"].forEach(id => {
    const el = document.getElementById(id);
    el?.addEventListener(el.tagName === "INPUT" ? "input" : "change", renderCollaboratorList);
});

renderCollaboratorList();

function showCollabProfile(id) {
    const c = exploreCollaborators.find(x => x.id === id);
    if (!c) return;

    document.getElementById("collabProfileName").textContent = c.name;
    document.getElementById("collabProfileRole").textContent = c.role;
    document.getElementById("collabProfileAvailabilityDot").className = "availability-dot " + c.availabilityDot;
    document.getElementById("collabProfileAvailabilityLabel").textContent = c.availability;
    document.getElementById("collabProfileBio").textContent = c.bio;
    document.getElementById("collabProfileExperience").innerHTML = c.experience.map(e => `<li>${e}</li>`).join("");
    document.getElementById("collabProfileSkills").innerHTML = c.skillLevels.map(s => `<div class="skill-row"><span>${s[0]}</span><span class="text-secondary">${s[1]}</span></div>`).join("");
    document.getElementById("collabProfileEducation").textContent = c.education;
    document.getElementById("collabProfileHighlights").innerHTML = c.highlights.map(h => `<li>${h}</li>`).join("");

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