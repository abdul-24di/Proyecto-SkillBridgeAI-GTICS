const profile = {
    nombre: "Paweł Kuna",
    cargo: "Backend Developer",
    nivel: "Junior",
    horas: 20,
    fotoUrl: null,
    about: "Desarrollador backend interesado en construir sistemas escalables y aprender sobre arquitectura cloud.",
    skills: [
        { id: 1, nombre: "Java", nivel: "Avanzado", certificado: true },
        { id: 2, nombre: "Spring Boot", nivel: "Avanzado", certificado: true },
        { id: 3, nombre: "MySQL", nivel: "Intermedio", certificado: true },
        { id: 4, nombre: "Git", nivel: "Básico", certificado: false }
    ],
    experience: [
        { id: 1, empresa: "Empresa X", cargo: "Full Stack Developer", descripcion: "Desarrollo de módulos internos y APIs REST.", inicio: "2024", fin: "Actualidad", actual: true },
        { id: 2, empresa: "Empresa Y", cargo: "Backend Developer", descripcion: "Mantenimiento de servicios backend en Java.", inicio: "2022", fin: "2024", actual: false }
    ],
    education: [
        { id: 1, institucion: "Universidad X", titulo: "Ingeniería de Software", inicio: "2022", fin: "Actualidad", actual: true }
    ],
    projectHistory: [
        { id: 1, nombre: "Proyecto X", rol: "Full Stack Developer", tags: ["Java", "Spring Boot", "MySQL"], estado: "Activo" },
        { id: 2, nombre: "Proyecto Z", rol: "Backend Developer", tags: ["Java", "Docker"], estado: "Finalizado" }
    ]
};

const levelBadgeClass = (nivel) => ({
    "Básico": "bg-secondary-lt",
    "Intermedio": "bg-blue-lt",
    "Avanzado": "bg-green-lt"
}[nivel] || "bg-secondary-lt");

// ---------------- Render: hero ----------------
const profileNameEl = document.getElementById("profileName");
const profileRoleEl = document.getElementById("profileRole");
const profileLevelBadge = document.getElementById("profileLevelBadge");
const profileHoursText = document.getElementById("profileHoursText");
const profileStatusDot = document.getElementById("profileStatusDot");
const profileStatusText = document.getElementById("profileStatusText");
const profilePhotoImg = document.getElementById("profilePhotoImg");
const profilePhotoPlaceholder = document.getElementById("profilePhotoPlaceholder");

function initials(name) {
    return name.split(" ").filter(Boolean).slice(0, 2).map(p => p[0]).join("").toUpperCase();
}

function renderHero() {
    profileNameEl.textContent = profile.nombre;
    profileRoleEl.textContent = profile.cargo;
    profileLevelBadge.textContent = profile.nivel;
    profileHoursText.textContent = `${profile.horas} horas/semana disponibles`;

    const disponible = profile.horas > 0;
    profileStatusDot.classList.toggle("offline", !disponible);
    profileStatusText.textContent = disponible ? "Disponible" : "No disponible";

    if (profile.fotoUrl) {
        profilePhotoImg.src = profile.fotoUrl;
        profilePhotoImg.classList.remove("d-none");
        profilePhotoPlaceholder.classList.add("d-none");
    } else {
        profilePhotoImg.classList.add("d-none");
        profilePhotoPlaceholder.classList.remove("d-none");
        profilePhotoPlaceholder.textContent = initials(profile.nombre);
    }
}

// ---------------- Perfil completado ----------------
const progressRing = document.getElementById("profileProgressRing");
const progressValue = document.getElementById("profileProgressValue");
const completionChecklist = document.getElementById("completionChecklist");

function renderCompletion() {
    const checks = [
        { done: !!profile.fotoUrl, label: "Agrega una foto de perfil" },
        { done: profile.about.trim().length > 0, label: "Agrega una descripción" },
        { done: profile.experience.length > 0, label: "Registra una experiencia" },
        { done: profile.skills.length > 0, label: "Agrega una habilidad" },
        { done: profile.education.length > 0, label: "Registra tu formación académica" }
    ];

    const done = checks.filter(c => c.done).length;
    const percent = Math.round((done / checks.length) * 100);

    progressRing.style.setProperty("--value", percent);
    progressValue.innerHTML = `${percent}%<small>Completado</small>`;

    const pending = checks.filter(c => !c.done);
    if (pending.length) {
        completionChecklist.classList.remove("empty");
        completionChecklist.innerHTML = pending.map(c => `<li>${c.label}</li>`).join("");
    } else {
        completionChecklist.classList.add("empty");
        completionChecklist.innerHTML = `<li>¡Tu perfil está completo!</li>`;
    }
}

// ---------------- Sobre mí ----------------
const aboutText = document.getElementById("aboutText");

function renderAbout() {
    aboutText.textContent = profile.about.trim() || "Aún no has agregado una descripción de tu perfil.";
}

// ---------------- Habilidades ----------------
const skillsList = document.getElementById("skillsList");

function renderSkills() {
    skillsList.innerHTML = profile.skills.length ? profile.skills.map(s => `
        <div class="skill-item" data-skill-id="${s.id}">
            <div>
                <div class="skill-item-name">${s.nombre}</div>
                ${s.certificado ? `<div class="skill-item-cert">
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 15l-3.5 2 1-4-3-2.6h3.8L12 6l1.7 4.4h3.8l-3 2.6 1 4z"/><circle cx="12" cy="10" r="8"/></svg>
                    Certificación adjunta
                </div>` : ""}
            </div>
            <div class="skill-item-actions">
                <span class="badge ${levelBadgeClass(s.nivel)}">${s.nivel}</span>
                <button type="button" class="skill-remove-btn" data-remove-skill="${s.id}" aria-label="Quitar habilidad">
                    <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 6l12 12M18 6L6 18"/></svg>
                </button>
            </div>
        </div>
    `).join("") : `<div class="empty-state">Aún no has agregado habilidades a tu perfil.</div>`;
}

skillsList.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-remove-skill]");
    if (!btn) return;
    const id = Number(btn.dataset.removeSkill);
    profile.skills = profile.skills.filter(s => s.id !== id);
    renderSkills();
    renderCompletion();
});

// ---------------- Experiencia ----------------
const experienceList = document.getElementById("experienceList");

function renderExperience() {
    experienceList.innerHTML = profile.experience.length ? profile.experience.map(exp => `
        <div class="timeline-entry">
            <div class="timeline-entry-title">${exp.cargo}${exp.empresa ? ` — ${exp.empresa}` : ""}</div>
            <div class="timeline-entry-meta">${exp.inicio || "?"} – ${exp.actual ? "Actualidad" : (exp.fin || "?")}</div>
            <div class="timeline-entry-desc">${exp.descripcion}</div>
        </div>
    `).join("") : `<div class="empty-state">Aún no has registrado experiencia profesional.</div>`;
}

// ---------------- Educación ----------------
const educationList = document.getElementById("educationList");

function renderEducation() {
    educationList.innerHTML = profile.education.length ? profile.education.map(edu => `
        <div class="timeline-entry">
            <div class="timeline-entry-title">${edu.titulo}</div>
            <div class="timeline-entry-meta">${edu.institucion || ""}</div>
            <div class="timeline-entry-desc">${edu.inicio || "?"} – ${edu.actual ? "Actualidad" : (edu.fin || "?")}</div>
        </div>
    `).join("") : `<div class="empty-state">Aún no has registrado formación académica.</div>`;
}

// ---------------- Historial de proyectos ----------------
const projectHistoryList = document.getElementById("projectHistoryList");
const projectStatusBadge = (estado) => estado === "Activo" ? "bg-green-lt" : "bg-secondary-lt";

function renderProjectHistory() {
    projectHistoryList.innerHTML = profile.projectHistory.length ? profile.projectHistory.map(p => `
        <div class="project-history-item">
            <div>
                <div class="fw-semibold">${p.nombre}</div>
                <div class="project-history-role">${p.rol}</div>
                <div class="project-history-tags">
                    ${p.tags.map(t => `<span class="badge bg-blue-lt">${t}</span>`).join("")}
                </div>
            </div>
            <span class="badge ${projectStatusBadge(p.estado)}">${p.estado}</span>
        </div>
    `).join("") : `<div class="empty-state">Aún no participas en ningún proyecto.</div>`;
}

// ---------------- Render general ----------------
function renderAll() {
    renderHero();
    renderCompletion();
    renderAbout();
    renderSkills();
    renderExperience();
    renderEducation();
    renderProjectHistory();
}

// ---------------- MODAL: Editar perfil ----------------
const editProfileModal = document.getElementById("editProfileModal");
const editNameInput = document.getElementById("editNameInput");
const editRoleInput = document.getElementById("editRoleInput");
const editHoursInput = document.getElementById("editHoursInput");
const editPhotoInput = document.getElementById("editPhotoInput");
const editPhotoPreviewImg = document.getElementById("editPhotoPreviewImg");
const editPhotoPreviewPlaceholder = document.getElementById("editPhotoPreviewPlaceholder");
const availabilityPreviewNote = document.getElementById("availabilityPreviewNote");
let pendingPhotoUrl = null;

function updateAvailabilityNote() {
    const horas = Number(editHoursInput.value) || 0;
    availabilityPreviewNote.textContent = horas > 0
        ? `🟢 Con ${horas} horas/semana quedarás marcado como "Disponible".`
        : `⚪ Con 0 horas/semana quedarás marcado como "No disponible".`;
}

editProfileModal.addEventListener("show.bs.modal", () => {
    editNameInput.value = profile.nombre;
    editRoleInput.value = profile.cargo;
    editHoursInput.value = profile.horas;
    pendingPhotoUrl = profile.fotoUrl;

    if (profile.fotoUrl) {
        editPhotoPreviewImg.src = profile.fotoUrl;
        editPhotoPreviewImg.classList.remove("d-none");
        editPhotoPreviewPlaceholder.classList.add("d-none");
    } else {
        editPhotoPreviewImg.classList.add("d-none");
        editPhotoPreviewPlaceholder.classList.remove("d-none");
    }
    editPhotoInput.value = "";
    updateAvailabilityNote();
});

editHoursInput.addEventListener("input", updateAvailabilityNote);

editPhotoInput.addEventListener("change", () => {
    const file = editPhotoInput.files?.[0];
    if (!file) return;
    pendingPhotoUrl = URL.createObjectURL(file);
    editPhotoPreviewImg.src = pendingPhotoUrl;
    editPhotoPreviewImg.classList.remove("d-none");
    editPhotoPreviewPlaceholder.classList.add("d-none");
});

document.getElementById("saveProfileBtn").addEventListener("click", () => {
    profile.nombre = editNameInput.value.trim() || profile.nombre;
    profile.cargo = editRoleInput.value.trim() || profile.cargo;
    profile.horas = Math.max(0, Number(editHoursInput.value) || 0);
    profile.fotoUrl = pendingPhotoUrl;
    renderHero();
    renderCompletion();
});

// ---------------- MODAL: Editar sobre mí ----------------
const editAboutModal = document.getElementById("editAboutModal");
const editAboutInput = document.getElementById("editAboutInput");

editAboutModal.addEventListener("show.bs.modal", () => {
    editAboutInput.value = profile.about;
});

document.getElementById("saveAboutBtn").addEventListener("click", () => {
    profile.about = editAboutInput.value.trim();
    renderAbout();
    renderCompletion();
});

// ---------------- MODAL: Agregar habilidad ----------------
const addSkillModal = document.getElementById("addSkillModal");
const skillNameInput = document.getElementById("skillNameInput");
const skillLevelInput = document.getElementById("skillLevelInput");
const skillEvidenceInput = document.getElementById("skillEvidenceInput");

addSkillModal.addEventListener("show.bs.modal", () => {
    skillNameInput.value = "";
    skillLevelInput.value = "";
    skillEvidenceInput.value = "";
});

document.getElementById("saveSkillBtn").addEventListener("click", () => {
    const nombre = skillNameInput.value.trim();
    const nivel = skillLevelInput.value;
    if (!nombre || !nivel) return;

    profile.skills.push({
        id: Date.now(),
        nombre,
        nivel,
        certificado: !!skillEvidenceInput.files?.length
    });
    renderSkills();
    renderCompletion();
});

// ---------------- MODAL: Agregar experiencia ----------------
const addExperienceModal = document.getElementById("addExperienceModal");
const expCompanyInput = document.getElementById("expCompanyInput");
const expRoleInput = document.getElementById("expRoleInput");
const expDescInput = document.getElementById("expDescInput");
const expStartInput = document.getElementById("expStartInput");
const expEndInput = document.getElementById("expEndInput");
const expCurrentInput = document.getElementById("expCurrentInput");

addExperienceModal.addEventListener("show.bs.modal", () => {
    [expCompanyInput, expRoleInput, expDescInput, expStartInput, expEndInput].forEach(el => el.value = "");
    expCurrentInput.checked = false;
});

document.getElementById("saveExperienceBtn").addEventListener("click", () => {
    const descripcion = expDescInput.value.trim();
    if (!descripcion) return;

    profile.experience.unshift({
        id: Date.now(),
        empresa: expCompanyInput.value.trim(),
        cargo: expRoleInput.value.trim() || "Colaborador",
        descripcion,
        inicio: expStartInput.value,
        fin: expEndInput.value,
        actual: expCurrentInput.checked
    });
    renderExperience();
    renderCompletion();
});

// ---------------- MODAL: Agregar formación ----------------
const addEducationModal = document.getElementById("addEducationModal");
const eduInstitutionInput = document.getElementById("eduInstitutionInput");
const eduDegreeInput = document.getElementById("eduDegreeInput");
const eduStartInput = document.getElementById("eduStartInput");
const eduEndInput = document.getElementById("eduEndInput");
const eduCurrentInput = document.getElementById("eduCurrentInput");

addEducationModal.addEventListener("show.bs.modal", () => {
    [eduInstitutionInput, eduDegreeInput, eduStartInput, eduEndInput].forEach(el => el.value = "");
    eduCurrentInput.checked = false;
});

document.getElementById("saveEducationBtn").addEventListener("click", () => {
    const titulo = eduDegreeInput.value.trim();
    if (!titulo) return;

    profile.education.unshift({
        id: Date.now(),
        institucion: eduInstitutionInput.value.trim(),
        titulo,
        inicio: eduStartInput.value,
        fin: eduEndInput.value,
        actual: eduCurrentInput.checked
    });
    renderEducation();
    renderCompletion();
});

renderAll();