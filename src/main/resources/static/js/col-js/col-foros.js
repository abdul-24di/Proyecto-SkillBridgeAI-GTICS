const projectForums = [
    { id: "proyectoA", nombre: "Proyecto A", posts: 12, ultimaActividad: "hace 20 min" },
    { id: "proyectoC", nombre: "Proyecto C", posts: 5, ultimaActividad: "ayer" }
];

const posts = [
    {
        id: 1, scope: "comunidad", etiqueta: "Seguridad",
        title: "¿Cómo implementaremos la autenticación?",
        excerpt: "Estamos evaluando JWT vs. sesiones para el login. ¿Qué opinan sobre expiración de tokens?",
        content: "Estamos evaluando JWT vs. sesiones para el login del proyecto. La principal duda es el tiempo de expiración del token y si conviene manejar refresh tokens desde el inicio o dejarlo para una siguiente iteración. ¿Alguien tiene experiencia implementando esto con Spring Security?",
        author: "Paweł Kuna", own: true, ago: "hace 2 horas", order: 1, baseVotes: 8, userVote: 0,
        replies: [
            { id: 101, author: "Juan Pérez", own: false, ago: "hace 1 hora", content: "Nosotros usamos JWT de corta duración (15 min) + refresh token de 7 días guardado en cookie httpOnly. Funciona bien para este tipo de proyectos.", baseVotes: 5, userVote: 0, solucion: true },
            { id: 102, author: "María Ruiz", own: false, ago: "hace 40 min", content: "Yo agregaría rotación del refresh token para evitar reuso si se filtra.", baseVotes: 2, userVote: 0, solucion: false }
        ]
    },
    {
        id: 2, scope: "comunidad", etiqueta: "Base de datos",
        title: "Recomendaciones para la estructura de la BD",
        excerpt: "¿Alguien tiene experiencia normalizando tablas de auditoría a gran escala?",
        content: "Estamos diseñando la tabla de auditoría (log_auditoria) y nos preocupa que crezca demasiado con el tiempo. ¿Alguien tiene experiencia particionando o archivando este tipo de tablas en MySQL?",
        author: "Juan Pérez", own: false, ago: "hace 5 horas", order: 2, baseVotes: 14, userVote: 0,
        replies: [
            { id: 201, author: "Daniel Acosta", own: false, ago: "hace 3 horas", content: "Particionar por rango de fecha (mensual) suele funcionar muy bien para logs de auditoría en MySQL.", baseVotes: 6, userVote: 0, solucion: false }
        ]
    },
    {
        id: 3, scope: "comunidad", etiqueta: "Carrera",
        title: "Certificaciones que valen la pena en 2026",
        excerpt: "Estoy evaluando certificarme en AWS o en Spring Professional, ¿cuál recomiendan primero?",
        content: "Estoy evaluando certificarme en AWS Solutions Architect o en Spring Professional este semestre. ¿Cuál me recomiendan empezar primero pensando en el tipo de proyectos que manejamos aquí?",
        author: "María Ruiz", own: false, ago: "hace 1 día", order: 3, baseVotes: 6, userVote: 0,
        replies: []
    },
    {
        id: 4, scope: "proyectoA", etiqueta: "Backend",
        title: "Definición del contrato del endpoint de activación",
        excerpt: "Antes de programar A6 necesitamos coordinar qué datos espera recibir el endpoint.",
        content: "Antes de empezar a programar la Historia A6 necesitamos coordinar qué datos espera recibir el endpoint de activación: ¿token por query param o en el body? ¿Devolvemos el usuario activado o solo un mensaje de éxito?",
        author: "Jeffie Lewzey", own: false, ago: "hace 20 min", order: 1, baseVotes: 4, userVote: 0,
        replies: [
            { id: 401, author: "Paweł Kuna", own: true, ago: "hace 5 min", content: "Propongo token por query param (?token=) y que el body de la petición POST solo lleve nombre y password.", baseVotes: 1, userVote: 0, solucion: false }
        ]
    },
    {
        id: 5, scope: "proyectoA", etiqueta: "Dudas",
        title: "¿Dónde validamos el límite de asignaciones activas?",
        excerpt: "¿La validación de MAX_ASIGNACIONES_POR_COLABORADOR va en el service o en el controller?",
        content: "¿La validación de MAX_ASIGNACIONES_POR_COLABORADOR debería ir en el service de Asignación o en el controller? Creo que en el service para poder reutilizarla desde distintos flujos (propuesta PM, propuesta RM, solicitud del colaborador).",
        author: "Paweł Kuna", own: true, ago: "hace 3 horas", order: 2, baseVotes: 9, userVote: 0,
        replies: []
    },
    {
        id: 6, scope: "proyectoC", etiqueta: "Anuncio",
        title: "Corte de sprint este viernes",
        excerpt: "Recordatorio: la demo del proyecto C será el viernes a las 4pm.",
        content: "Recordatorio: la demo del proyecto C será este viernes a las 4pm. Por favor tener listas sus tareas marcadas como \"Listo para revisar\" antes del jueves en la noche.",
        author: "Mallory Diaz", own: false, ago: "ayer", order: 1, baseVotes: 3, userVote: 0,
        replies: []
    }
];

const tagClass = (tag) => ({
    Seguridad: "bg-red-lt", "Base de datos": "bg-purple-lt", Carrera: "bg-cyan-lt",
    Backend: "bg-blue-lt", Dudas: "bg-orange-lt", Anuncio: "bg-green-lt"
}[tag] || "bg-secondary-lt");

const totalVotes = (item) => item.baseVotes + item.userVote;
const initials = (name) => name.split(" ").filter(Boolean).slice(0, 2).map(p => p[0]).join("").toUpperCase();

const voteIcon = (dir) => dir === "up"
    ? `<svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><path d="M6 15l6-6 6 6"/></svg>`
    : `<svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9l6 6 6-6"/></svg>`;

function renderPost(post, container) {
    return `<article class="card forum-post" data-post-id="${post.id}" data-container="${container}">
        <div class="forum-post-body">
            <div class="vote-box">
                <button type="button" class="vote-button ${post.userVote === 1 ? "active" : ""}" data-vote="1" data-post-id="${post.id}" data-container="${container}" aria-label="Votar a favor">${voteIcon("up")}</button>
                <div class="vote-count">${totalVotes(post)}</div>
                <button type="button" class="vote-button down ${post.userVote === -1 ? "active" : ""}" data-vote="-1" data-post-id="${post.id}" data-container="${container}" aria-label="Votar en contra">${voteIcon("down")}</button>
            </div>
            <div class="flex-fill">
                <span class="badge ${tagClass(post.etiqueta)}">#${post.etiqueta}</span>
                <div class="post-title mt-1" data-open-post="${post.id}">${post.title}</div>
                <div class="post-excerpt">${post.excerpt}</div>
                <div class="post-meta">Publicado por ${post.author}${post.own ? " (tú)" : ""} · ${post.ago}</div>
            </div>
            <div class="reply-count"><strong>${post.replies.length}</strong>respuestas</div>
        </div>
    </article>`;
}

function bindVotes(root) {
    root.addEventListener("click", (event) => {
        const btn = event.target.closest("[data-vote]");
        if (!btn) return;
        event.stopPropagation();

        const requested = Number(btn.dataset.vote);

        if (btn.dataset.replyId) {
            const reply = currentPost?.replies.find(r => r.id === Number(btn.dataset.replyId));
            if (!reply) return;
            reply.userVote = reply.userVote === requested ? 0 : requested;
            renderReplies();
            return;
        }

        const post = posts.find(p => p.id === Number(btn.dataset.postId));
        if (!post) return;
        post.userVote = post.userVote === requested ? 0 : requested;
        renderComunidad();
        renderProyectoForo();
        if (currentPost && currentPost.id === post.id) renderPostDetailCard();
    });
}

// ---------------- Vista Comunidad ----------------
const forumPostsBox = document.getElementById("forumPosts");
const forumSearch = document.getElementById("forumSearch");
const forumShow = document.getElementById("forumShow");
const forumSort = document.getElementById("forumSort");

function renderComunidad() {
    if (!forumPostsBox) return;
    const term = forumSearch.value.trim().toLowerCase();
    let filtered = posts.filter(p => p.scope === "comunidad");

    if (forumShow.value === "mias") filtered = filtered.filter(p => p.own);
    if (forumShow.value === "otros") filtered = filtered.filter(p => !p.own);
    if (term) filtered = filtered.filter(p =>
        p.title.toLowerCase().includes(term) || p.excerpt.toLowerCase().includes(term) || p.etiqueta.toLowerCase().includes(term)
    );

    filtered.sort(forumSort.value === "votes"
        ? (a, b) => totalVotes(b) - totalVotes(a)
        : (a, b) => a.order - b.order);

    forumPostsBox.innerHTML = filtered.length
        ? filtered.map(p => renderPost(p, "forumPosts")).join("")
        : `<div class="empty-state">No se encontraron publicaciones con esos filtros.</div>`;
}

[forumSearch, forumShow, forumSort].forEach(el => el && el.addEventListener("input", renderComunidad));

// ---------------- Vista Mis Proyectos (foros por proyecto) ----------------
const projectForumGrid = document.getElementById("projectForumGrid");

function renderProjectForums() {
    if (!projectForumGrid) return;
    projectForumGrid.innerHTML = projectForums.map(f => `
        <div class="col-md-6 col-xl-4">
            <div class="card project-forum-card">
                <div class="card-body">
                    <div class="project-forum-icon bg-yellow-lt text-yellow">
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z"/></svg>
                    </div>
                    <div class="fw-bold mt-2">${f.nombre}</div>
                    <div class="project-forum-meta">${f.posts} publicaciones · Última actividad ${f.ultimaActividad}</div>
                    <button type="button" class="btn btn-sm btn-outline-primary mt-3 w-100" data-open-forum="${f.id}">Ver foro →</button>
                </div>
            </div>
        </div>
    `).join("");
}

// ---------------- Vista de un foro de proyecto específico ----------------
const proyectoForoView = document.getElementById("proyectoForoView");
const proyectoForumPosts = document.getElementById("proyectoForumPosts");
const proyectoForoNombre = document.getElementById("proyectoForoNombre");
let currentProjectForum = null;

function renderProyectoForo() {
    if (!currentProjectForum || !proyectoForumPosts) return;
    const filtered = posts.filter(p => p.scope === currentProjectForum.id);
    proyectoForumPosts.innerHTML = filtered.length
        ? filtered.map(p => renderPost(p, "proyectoForumPosts")).join("")
        : `<div class="empty-state">Este proyecto aún no tiene publicaciones.</div>`;
}

function openProjectForum(id) {
    currentProjectForum = projectForums.find(f => f.id === id);
    if (!currentProjectForum) return;
    proyectoForoNombre.textContent = `Foro: ${currentProjectForum.nombre}`;
    renderProyectoForo();
    hideAllViews();
    proyectoForoView.classList.remove("d-none");
}

document.getElementById("backToProjectForums")?.addEventListener("click", (e) => {
    e.preventDefault();
    hideAllViews();
    views.proyectos.classList.remove("d-none");
    scopeButtons.forEach(b => b.classList.remove("active"));
    document.querySelector('[data-scope="proyectos"]').classList.add("active");
});

projectForumGrid?.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-open-forum]");
    if (btn) openProjectForum(btn.dataset.openForum);
});

// ---------------- Vista de detalle de publicación ----------------
const postDetailView = document.getElementById("postDetailView");
const postDetailCard = document.getElementById("postDetailCard");
const postDetailScopeName = document.getElementById("postDetailScopeName");
const postDetailTitleCrumb = document.getElementById("postDetailTitleCrumb");
const postDetailReplyCount = document.getElementById("postDetailReplyCount");
const replyList = document.getElementById("replyList");
const replySort = document.getElementById("replySort");
const replyInput = document.getElementById("replyInput");
const submitReplyBtn = document.getElementById("submitReplyBtn");

let currentPost = null;
let returnOrigin = "comunidad"; // "comunidad" o el id del proyecto

function renderPostDetailCard() {
    if (!currentPost || !postDetailCard) return;
    postDetailCard.innerHTML = `
        <div class="card-body post-detail-body">
            <div class="vote-box">
                <button type="button" class="vote-button ${currentPost.userVote === 1 ? "active" : ""}" data-vote="1" data-post-id="${currentPost.id}" aria-label="Votar a favor">${voteIcon("up")}</button>
                <div class="vote-count">${totalVotes(currentPost)}</div>
                <button type="button" class="vote-button down ${currentPost.userVote === -1 ? "active" : ""}" data-vote="-1" data-post-id="${currentPost.id}" aria-label="Votar en contra">${voteIcon("down")}</button>
            </div>
            <div class="flex-fill">
                <div class="post-author-row">
                    <span class="avatar post-avatar bg-blue-lt text-blue">${initials(currentPost.author)}</span>
                    <div class="flex-fill">
                        <div class="fw-semibold">${currentPost.author}${currentPost.own ? " (tú)" : ""}</div>
                        <div class="small text-secondary">${currentPost.ago}</div>
                    </div>
                    <span class="badge bg-secondary-lt">Publicación</span>
                </div>
                <div class="post-tags mt-3">
                    <span class="badge ${tagClass(currentPost.etiqueta)}">#${currentPost.etiqueta}</span>
                </div>
                <div class="post-content">${currentPost.content}</div>
                <div class="post-footer">
                    <span>${currentPost.replies.length} respuestas</span>
                    <span>${currentPost.scope === "comunidad" ? "Comunidad" : "Foro de proyecto"}</span>
                </div>
            </div>
        </div>
    `;
}

function renderReplies() {
    if (!currentPost || !replyList) return;
    const sorted = [...currentPost.replies].sort(replySort.value === "votes"
        ? (a, b) => totalVotes(b) - totalVotes(a)
        : (a, b) => b.id - a.id);

    postDetailReplyCount.textContent = `${currentPost.replies.length} respuestas`;

    replyList.innerHTML = sorted.length ? sorted.map(reply => `
        <div class="reply-row" data-reply-id="${reply.id}">
            <div class="reply-vote-box">
                <button type="button" class="vote-button ${reply.userVote === 1 ? "active" : ""}" data-vote="1" data-reply-id="${reply.id}" aria-label="Votar a favor">${voteIcon("up")}</button>
                <div class="vote-count">${totalVotes(reply)}</div>
                <button type="button" class="vote-button down ${reply.userVote === -1 ? "active" : ""}" data-vote="-1" data-reply-id="${reply.id}" aria-label="Votar en contra">${voteIcon("down")}</button>
            </div>
            <div class="flex-fill">
                <div class="d-flex align-items-center gap-2 flex-wrap">
                    <span class="avatar reply-avatar bg-purple-lt text-purple">${initials(reply.author)}</span>
                    <div class="fw-semibold">${reply.author}${reply.own ? " (tú)" : ""}</div>
                    ${reply.solucion ? '<span class="badge bg-green-lt">Solución marcada</span>' : ""}
                </div>
                <div class="small text-secondary">${reply.ago}</div>
                <div class="reply-content">${reply.content}</div>
                ${currentPost.own && !reply.solucion ? `<button type="button" class="btn btn-outline-success btn-sm reply-solution-btn" data-mark-solution="${reply.id}">Marcar como solución</button>` : ""}
            </div>
        </div>
    `).join("") : `<div class="empty-replies">Aún no hay respuestas. Sé el primero en responder.</div>`;
}

function renderPostDetail() {
    renderPostDetailCard();
    renderReplies();
}

function openPostDetail(postId, origin) {
    const post = posts.find(p => p.id === Number(postId));
    if (!post) return;
    currentPost = post;
    returnOrigin = origin;

    postDetailScopeName.textContent = origin === "comunidad"
        ? "Comunidad"
        : (projectForums.find(f => f.id === origin)?.nombre || "Proyecto");
    postDetailTitleCrumb.textContent = post.title;

    renderPostDetail();
    hideAllViews();
    postDetailView.classList.remove("d-none");
}

document.getElementById("backToForumList")?.addEventListener("click", (e) => {
    e.preventDefault();
    currentPost = null;
    hideAllViews();
    if (returnOrigin === "comunidad") {
        views.comunidad.classList.remove("d-none");
        scopeButtons.forEach(b => b.classList.remove("active"));
        document.querySelector('[data-scope="comunidad"]').classList.add("active");
    } else {
        openProjectForum(returnOrigin);
        scopeButtons.forEach(b => b.classList.remove("active"));
        document.querySelector('[data-scope="proyectos"]').classList.add("active");
    }
});

replySort?.addEventListener("change", renderReplies);

replyList?.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-mark-solution]");
    if (!btn || !currentPost) return;
    const replyId = Number(btn.dataset.markSolution);
    currentPost.replies.forEach(r => { r.solucion = r.id === replyId; });
    renderReplies();
});

submitReplyBtn?.addEventListener("click", () => {
    if (!currentPost) return;
    const text = replyInput.value.trim();
    if (!text) return;
    currentPost.replies.push({
        id: Date.now(),
        author: "Paweł Kuna",
        own: true,
        ago: "justo ahora",
        content: text,
        baseVotes: 0,
        userVote: 0,
        solucion: false
    });
    replyInput.value = "";
    renderReplies();
    renderComunidad();
    renderProyectoForo();
});

// Click en una publicación (comunidad o foro de proyecto) abre el detalle
[forumPostsBox, proyectoForumPosts].forEach(box => {
    box?.addEventListener("click", (e) => {
        if (e.target.closest("[data-vote]")) return;
        const card = e.target.closest("[data-post-id]");
        if (!card) return;
        const origin = box === forumPostsBox ? "comunidad" : currentProjectForum?.id;
        if (origin) openPostDetail(card.dataset.postId, origin);
    });
});

// ---------------- Tabs Comunidad / Mis proyectos ----------------
const views = {
    comunidad: document.getElementById("comunidadView"),
    proyectos: document.getElementById("proyectosView")
};
const scopeButtons = [...document.querySelectorAll("[data-scope]")];

function hideAllViews() {
    Object.values(views).forEach(v => v.classList.add("d-none"));
    proyectoForoView.classList.add("d-none");
    postDetailView.classList.add("d-none");
}

scopeButtons.forEach(btn => btn.addEventListener("click", () => {
    scopeButtons.forEach(b => b.classList.remove("active"));
    btn.classList.add("active");
    currentPost = null;
    hideAllViews();
    views[btn.dataset.scope].classList.remove("d-none");
}));

bindVotes(document.querySelector("main.page-wrap"));
renderComunidad();
renderProjectForums();