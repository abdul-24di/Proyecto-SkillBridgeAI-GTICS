/* ================= PERFILES DE INTEGRANTES (modal "Ver Perfil") ================= */
const profiles = {
    pawel: {
        name: "Paweł Kuna", role: "Backend Developer", seniority: "Junior",
        availability: "15 horas/semana disponibles", available: true,
        bio: "Backend developer enfocado en APIs REST y bases de datos relacionales.",
        experience: [
            "Backend Developer — SkillBridge AI: Desarrollo de servicios REST con Spring Boot.",
            "Practicante de desarrollo — Empresa X: Soporte en mantenimiento de APIs internas."
        ],
        skills: [["Java", "Intermedio"], ["Spring Boot", "Intermedio"], ["MySQL", "Básico"]],
        education: "Ingeniería de Software en Universidad X",
        highlights: ["Proyecto A: Java · Spring Boot · MySQL"],
        inProject: { rol: "Colaborador", especialidad: "Backend Development", incorporacion: "12/06/2026" }
    },
    jeffie: {
        name: "Jeffie Lewzey", role: "Full Stack", seniority: "Senior",
        availability: "20 horas/semana disponibles", available: true,
        bio: "Persona especializada en desarrollo full stack y liderazgo técnico de equipos.",
        experience: [
            "Senior Full Stack Developer: Desarrollo de aplicaciones web y liderazgo técnico.",
            "Full Stack Developer: Desarrollo e integración de aplicaciones empresariales."
        ],
        skills: [["Java", "Avanzado"], ["Spring Boot", "Avanzado"], ["MySQL", "Intermedio"], ["Git", "Básico"]],
        education: "Ingeniería de Software en Universidad X",
        highlights: ["Proyecto X: Java · Spring Boot · MySQL"],
        inProject: { rol: "Project Manager", especialidad: "Full Stack Development", incorporacion: "10/08/2026" }
    },
    mallory: {
        name: "Mallory Hulme", role: "Frontend Developer", seniority: "Semi Senior",
        availability: "10 horas/semana disponibles", available: true,
        bio: "Desarrolladora frontend enfocada en interfaces accesibles y de alto rendimiento.",
        experience: ["Frontend Developer — SkillBridge AI: Construcción de interfaces con React."],
        skills: [["React", "Avanzado"], ["JavaScript", "Avanzado"], ["CSS", "Intermedio"]],
        education: "Ingeniería de Sistemas en Universidad Y",
        highlights: ["Proyecto A: React · TypeScript"],
        inProject: { rol: "Colaborador", especialidad: "Frontend Development", incorporacion: "02/07/2026" }
    },
    dunn: {
        name: "Dunn Slane", role: "UI/UX Designer", seniority: "Semi Senior",
        availability: "12 horas/semana disponibles", available: false,
        bio: "Diseñador UI/UX enfocado en investigación de usuarios y sistemas de diseño.",
        experience: ["UI/UX Designer — SkillBridge AI: Diseño de flujos y sistema de componentes."],
        skills: [["Figma", "Avanzado"], ["Investigación UX", "Intermedio"]],
        education: "Diseño Gráfico en Universidad Z",
        highlights: ["Proyecto A: Figma · Design System"],
        inProject: { rol: "Colaborador", especialidad: "UI/UX Design", incorporacion: "20/07/2026" }
    }
};

function openMemberProfile(id) {
    const p = profiles[id];
    if (!p) return;
    document.getElementById("memberModalName").textContent = p.name;
    document.getElementById("memberModalRoleSeniority").textContent = p.role + " · " + p.seniority;
    document.getElementById("memberModalAvailabilityText").textContent = p.availability;
    document.getElementById("memberModalAvailabilityDot").className = "availability-dot " + (p.available ? "bg-green" : "bg-secondary");
    document.getElementById("memberModalAvailabilityLabel").textContent = p.available ? "Disponible" : "No disponible";
    document.getElementById("memberModalBio").textContent = p.bio;
    document.getElementById("memberModalExperience").innerHTML = p.experience.map(e => `<li>${e}</li>`).join("");
    document.getElementById("memberModalSkills").innerHTML = p.skills.map(s => `<div class="skill-row"><span>${s[0]}</span><span class="text-secondary">${s[1]}</span></div>`).join("");
    document.getElementById("memberModalEducation").textContent = p.education;
    document.getElementById("memberModalHighlights").innerHTML = p.highlights.map(h => `<li>${h}</li>`).join("");
    document.getElementById("memberModalInRol").textContent = p.inProject.rol;
    document.getElementById("memberModalInEspecialidad").textContent = p.inProject.especialidad;
    document.getElementById("memberModalInIncorporacion").textContent = p.inProject.incorporacion;
}

document.querySelectorAll("[data-col-profile-id]").forEach(btn => {
    btn.addEventListener("click", () => openMemberProfile(btn.dataset.colProfileId));
});

const memberSearch = document.getElementById("memberSearch");
memberSearch?.addEventListener("input", () => {
    const q = memberSearch.value.trim().toLowerCase();
    document.querySelectorAll(".member-grid-item").forEach(item => {
        const matches = (item.dataset.memberName + " " + item.dataset.memberRole).toLowerCase().includes(q);
        item.style.display = matches ? "" : "none";
    });
});

/* ================= ACTIVIDADES ================= */
const activities = [
    {
        id: 1, titulo: "Implementar login", descripcion: "Implementar el sistema de autenticación de usuarios.",
        asignadoA: "Tú", esPropia: true, prioridad: "Alta", fechaLimite: "10 Sep 2026",
        estado: "En progreso", evidencias: [], comentario: "", comentarioDevolucion: null
    },
    {
        id: 2, titulo: "Diseñar dashboard", descripcion: "Diseñar la interfaz principal del dashboard.",
        asignadoA: "Juan Pérez", esPropia: false, prioridad: "Media", fechaLimite: "12 Sep 2026",
        estado: "En revisión", evidencias: ["dashboard.fig"], comentario: "Ya está terminada.", comentarioDevolucion: null
    },
    {
        id: 3, titulo: "Crear API usuarios", descripcion: "Exponer los endpoints REST para la gestión de usuarios.",
        asignadoA: "Pedro", esPropia: false, prioridad: "Alta", fechaLimite: "05 Sep 2026",
        estado: "Completa", estadoEntrega: "A tiempo", evidencias: ["api-usuarios.postman.json"],
        comentario: "Entregado con pruebas.", comentarioDevolucion: null
    },
    {
        id: 4, titulo: "Configurar BD", descripcion: "Configurar el motor de base de datos y las migraciones iniciales.",
        asignadoA: "Tú", esPropia: true, prioridad: "Alta", fechaLimite: "08 Sep 2026",
        estado: "Pendiente", evidencias: [], comentario: "", comentarioDevolucion: null
    },
    {
        id: 5, titulo: "Pruebas unitarias del módulo de login", descripcion: "Cubrir con pruebas unitarias el módulo de autenticación.",
        asignadoA: "Tú", esPropia: true, prioridad: "Media", fechaLimite: "01 Sep 2026",
        estado: "En progreso", evidencias: ["tests-login.png"],
        comentario: "Falta cubrir el caso de token expirado.",
        comentarioDevolucion: "Por favor agrega pruebas para credenciales inválidas antes de reenviar."
    }
];

let currentActivityId = null;

function estadoBadgeClass(estado) {
    return { "Pendiente": "bg-secondary-lt", "En progreso": "bg-yellow-lt", "En revisión": "bg-blue-lt", "Completa": "bg-green-lt" }[estado] || "bg-secondary-lt";
}

function prioridadBadgeClass(p) {
    return p === "Alta" ? "bg-red-lt text-red" : p === "Media" ? "bg-yellow-lt" : "bg-green-lt";
}

function renderActivityTable() {
    const q = (document.getElementById("activitySearch")?.value || "").trim().toLowerCase();
    const status = document.getElementById("activityStatusFilter")?.value || "all";
    const assigned = document.getElementById("activityAssignedFilter")?.value || "all";

    const filtered = activities.filter(a => {
        const matchesSearch = a.titulo.toLowerCase().includes(q);
        const matchesStatus = status === "all" || a.estado === status;
        const asignadoTipo = a.esPropia ? "Tú" : "Otros";
        const matchesAssigned = assigned === "all" || asignadoTipo === assigned;
        return matchesSearch && matchesStatus && matchesAssigned;
    });

    const tbody = document.querySelector("#activityTable tbody");
    tbody.innerHTML = filtered.map(a => `
    <tr class="activity-row" data-activity-id="${a.id}">
      <td class="fw-semibold">${a.titulo}</td>
      <td>${a.asignadoA}</td>
      <td><span class="badge ${prioridadBadgeClass(a.prioridad)}">${a.prioridad}</span></td>
      <td><span class="badge ${estadoBadgeClass(a.estado)}">${a.estado}</span></td>
      <td class="text-end">
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6l6 6-6 6"/></svg>
      </td>
    </tr>`).join("") || `<tr><td colspan="5" class="empty-state">No se encontraron actividades.</td></tr>`;

    tbody.querySelectorAll(".activity-row").forEach(row => {
        row.addEventListener("click", () => openActivityModal(Number(row.dataset.activityId)));
    });
}

function renderEvidenceChips(containerId, evidencias, removable) {
    const container = document.getElementById(containerId);
    if (!evidencias.length) {
        container.innerHTML = `<span class="text-secondary small">Sin evidencias adjuntas.</span>`;
        return;
    }
    container.innerHTML = evidencias.map((e, i) => `
    <span class="evidence-chip">
      <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 3v4a1 1 0 0 0 1 1h4"/><path d="M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2z"/></svg>
      ${e}
      ${removable ? `<button type="button" class="evidence-remove" data-evidence-index="${i}" aria-label="Quitar">&times;</button>` : ""}
    </span>`).join("");

    if (removable) {
        container.querySelectorAll(".evidence-remove").forEach(btn => {
            btn.addEventListener("click", () => {
                const a = activities.find(x => x.id === currentActivityId);
                a.evidencias.splice(Number(btn.dataset.evidenceIndex), 1);
                renderEvidenceChips(containerId, a.evidencias, true);
            });
        });
    }
}

function openActivityModal(id) {
    const a = activities.find(x => x.id === id);
    if (!a) return;
    currentActivityId = id;

    document.getElementById("activityModalTitle").textContent = a.titulo;

    const estadoBadge = document.getElementById("activityModalEstado");
    estadoBadge.textContent = a.estado === "Completa" ? `Completa · ${a.estadoEntrega}` : a.estado;
    estadoBadge.className = "badge " + estadoBadgeClass(a.estado);

    const prioridadBadge = document.getElementById("activityModalPrioridad");
    prioridadBadge.textContent = a.prioridad;
    prioridadBadge.className = "badge " + prioridadBadgeClass(a.prioridad);

    document.getElementById("activityModalAsignado").textContent = a.asignadoA;
    document.getElementById("activityModalFecha").textContent = a.fechaLimite;
    document.getElementById("activityModalDescripcion").textContent = a.descripcion;

    const returnedAlert = document.getElementById("activityReturnedAlert");
    if (a.comentarioDevolucion) {
        returnedAlert.classList.remove("d-none");
        document.getElementById("activityReturnedText").textContent = a.comentarioDevolucion;
    } else {
        returnedAlert.classList.add("d-none");
    }

    const editable = a.esPropia && (a.estado === "Pendiente" || a.estado === "En progreso");
    const readonlyBlock = document.getElementById("activityReadonlyBlock");
    const editableBlock = document.getElementById("activityEditableBlock");
    const footerReadonly = document.getElementById("activityFooterReadonly");
    const footerEdit = document.getElementById("activityFooterEdit");
    const inReviewNotice = document.getElementById("activityInReviewNotice");

    if (editable) {
        readonlyBlock.style.display = "none";
        editableBlock.style.display = "block";
        footerReadonly.style.display = "none";
        footerEdit.style.display = "flex";
        document.getElementById("activityCommentInput").value = a.comentario || "";
        renderEvidenceChips("activityModalEvidenciasEditable", a.evidencias, true);
    } else {
        editableBlock.style.display = "none";
        readonlyBlock.style.display = "block";
        footerEdit.style.display = "none";
        footerReadonly.style.display = "flex";
        renderEvidenceChips("activityModalEvidenciasReadonly", a.evidencias, false);
        document.getElementById("activityModalComentarioReadonly").textContent = a.comentario || "Sin comentarios.";
        inReviewNotice.style.display = (a.esPropia && a.estado === "En revisión") ? "block" : "none";
    }

    const modalEl = document.getElementById("activityModal");
    const Modal = window.bootstrap?.Modal || window.tabler?.bootstrap?.Modal;
    Modal.getOrCreateInstance(modalEl).show();
}

document.getElementById("activityEvidenceInput")?.addEventListener("change", (e) => {
    const a = activities.find(x => x.id === currentActivityId);
    Array.from(e.target.files).forEach(f => a.evidencias.push(f.name));
    renderEvidenceChips("activityModalEvidenciasEditable", a.evidencias, true);
    e.target.value = "";
});

document.getElementById("activitySaveDraftBtn")?.addEventListener("click", () => {
    const a = activities.find(x => x.id === currentActivityId);
    a.comentario = document.getElementById("activityCommentInput").value;
    const modalEl = document.getElementById("activityModal");
    (window.bootstrap?.Modal || window.tabler?.bootstrap?.Modal).getOrCreateInstance(modalEl).hide();
});

document.getElementById("activitySubmitReviewBtn")?.addEventListener("click", () => {
    const a = activities.find(x => x.id === currentActivityId);
    a.comentario = document.getElementById("activityCommentInput").value;
    a.estado = "En revisión";
    a.comentarioDevolucion = null;
    renderActivityTable();
    const modalEl = document.getElementById("activityModal");
    (window.bootstrap?.Modal || window.tabler?.bootstrap?.Modal).getOrCreateInstance(modalEl).hide();
});

["activitySearch", "activityStatusFilter", "activityAssignedFilter"].forEach(id => {
    const el = document.getElementById(id);
    el?.addEventListener(el.tagName === "INPUT" ? "input" : "change", renderActivityTable);
});

renderActivityTable();

/* ================= DOCUMENTOS ================= */
function filterDocs() {
    const q = (document.getElementById("docSearch")?.value || "").trim().toLowerCase();
    const cat = document.getElementById("docCategoryFilter")?.value || "all";
    document.querySelectorAll("#docTable tbody tr").forEach(row => {
        const matchesSearch = row.textContent.toLowerCase().includes(q);
        const matchesCat = cat === "all" || row.dataset.docType === cat;
        row.style.display = (matchesSearch && matchesCat) ? "" : "none";
    });
}

document.getElementById("docSearch")?.addEventListener("input", filterDocs);
document.getElementById("docCategoryFilter")?.addEventListener("change", filterDocs);

/* ================= FORO (tab del proyecto) ================= */
if (window.location.hash === "#tab-foro") {
    const tabBtn = document.getElementById("tabForoBtn");
    const Tab = window.bootstrap?.Tab || window.tabler?.bootstrap?.Tab;
    if (tabBtn && Tab) Tab.getOrCreateInstance(tabBtn).show();
}

document.getElementById("projectForumSearch")?.addEventListener("input", function () {
    const q = this.value.trim().toLowerCase();
    document.querySelectorAll("#projectForumList .forum-post-card").forEach(card => {
        card.style.display = card.textContent.toLowerCase().includes(q) ? "" : "none";
    });
});

/* ================= CHAT (tab del proyecto) ================= */
function sendChatMessage() {
    const input = document.getElementById("chatInput");
    const text = input.value.trim();
    if (!text) return;
    const messages = document.getElementById("chatMessages");
    const d = new Date();
    const time = d.getHours().toString().padStart(2, "0") + ":" + d.getMinutes().toString().padStart(2, "0");
    const row = document.createElement("div");
    row.className = "chat-msg chat-msg-own";
    row.innerHTML = `<div class="chat-bubble"><div class="chat-bubble-author">Paweł Kuna<span class="chat-bubble-time">${time}</span></div>${text}</div>`;
    messages.appendChild(row);
    messages.scrollTop = messages.scrollHeight;
    input.value = "";
}

document.getElementById("chatSendBtn")?.addEventListener("click", sendChatMessage);
document.getElementById("chatInput")?.addEventListener("keydown", (e) => { if (e.key === "Enter") sendChatMessage(); });

document.querySelectorAll(".chat-contact").forEach(c => {
    c.addEventListener("click", () => {
        document.querySelectorAll(".chat-contact").forEach(x => x.classList.remove("active"));
        c.classList.add("active");
    });
});
// ---------------- Foro (tab) — reusa los datos y el componente de tarjeta reales ----------------
// Esta página muestra el proyecto A, así que filtramos por ese scope. Cuando se conecte
// a la BD real, este id vendrá del proyecto que efectivamente se está viendo.
const projectForumList = document.getElementById("projectForumList");
if (projectForumList && typeof posts !== "undefined") {
    const projectPosts = posts.filter(p => p.scope === "proyectoA");
    projectForumList.innerHTML = projectPosts.length
        ? projectPosts.map(p => renderPost(p, "projectForumList")).join("")
        : `<div class="empty-state">Este proyecto aún no tiene publicaciones.</div>`;

    projectForumList.addEventListener("click", (e) => {
        if (e.target.closest("[data-open-post]") || e.target.closest("[data-vote]")) {
            window.location.href = "col-foros.html";
        }
    });
}
