// projectForums, posts y las funciones de render/formato viven en foro-data.js
// (compartido con la pestaña "Foro" del detalle de proyecto) — cargar ese script
// antes que este en el HTML.

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


