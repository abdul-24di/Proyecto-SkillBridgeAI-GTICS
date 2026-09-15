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

document.getElementById("projectSearch")?.addEventListener("input", (e) => {
    const q = e.target.value.trim().toLowerCase();
    document.querySelectorAll("#projectAccordion .project-accordion-item").forEach(item => {
        const nombre = (item.dataset.projectName || "").toLowerCase();
        const texto = item.textContent.toLowerCase();
        item.classList.toggle("d-none", q.length > 0 && !nombre.includes(q) && !texto.includes(q));
    });
});

document.querySelectorAll("#projectAccordion [data-open-apply]").forEach(btn => {
    btn.addEventListener("click", (e) => {
        e.stopPropagation();
        document.getElementById("applyModalTitle").textContent = "Postulación al " + btn.dataset.projectName;
        document.getElementById("applyProyectoId").value = btn.dataset.projectId;
        document.getElementById("applyMessageInput").value = "";

        const modalEl = document.getElementById("applyModal");
        const Modal = window.bootstrap?.Modal || window.tabler?.bootstrap?.Modal;
        Modal.getOrCreateInstance(modalEl).show();
    });
});

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