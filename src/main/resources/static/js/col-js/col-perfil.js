const mock = {
    experience: [
        { id: 1, empresa: "Empresa X", cargo: "Full Stack Developer", descripcion: "Desarrollo de módulos internos y APIs REST.", inicio: "2024", fin: "Actualidad", actual: true },
        { id: 2, empresa: "Empresa Y", cargo: "Backend Developer", descripcion: "Mantenimiento de servicios backend en Java.", inicio: "2022", fin: "2024", actual: false }
    ],
    projectHistory: [
        { id: 1, nombre: "Proyecto X", rol: "Full Stack Developer", tags: ["Java", "Spring Boot", "MySQL"], estado: "Activo" },
        { id: 2, nombre: "Proyecto Z", rol: "Backend Developer", tags: ["Java", "Docker"], estado: "Finalizado" }
    ]
};

// ---------------- Experiencia ----------------
const experienceList = document.getElementById("experienceList");

function renderExperience() {
    experienceList.innerHTML = mock.experience.length ? mock.experience.map(exp => `
        <div class="timeline-entry">
            <div class="timeline-entry-title">${exp.cargo}${exp.empresa ? ` — ${exp.empresa}` : ""}</div>
            <div class="timeline-entry-meta">${exp.inicio || "?"} – ${exp.actual ? "Actualidad" : (exp.fin || "?")}</div>
            <div class="timeline-entry-desc">${exp.descripcion}</div>
        </div>
    `).join("") : `<div class="empty-state">Aún no has registrado experiencia profesional.</div>`;
}

// ---------------- Historial de proyectos ----------------
const projectHistoryList = document.getElementById("projectHistoryList");
const projectStatusBadge = (estado) => estado === "Activo" ? "bg-green-lt" : "bg-secondary-lt";

function renderProjectHistory() {
    projectHistoryList.innerHTML = mock.projectHistory.length ? mock.projectHistory.map(p => `
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

// ---------------- MODAL: Agregar formación ----------------
const eduCurrentInput = document.getElementById("eduCurrentInput");
const eduEndInput = document.getElementById("eduEndInput");

eduCurrentInput?.addEventListener("change", () => {
    eduEndInput.disabled = eduCurrentInput.checked;
    if (eduCurrentInput.checked) eduEndInput.value = "";
});

renderExperience();
renderProjectHistory();