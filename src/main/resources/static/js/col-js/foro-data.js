// Fuente de datos compartida del foro del colaborador.
// La usan tanto col-foros.js (vista dedicada de Foros) como col-detalle-proyecto.html
// (tab "Foro" dentro del detalle de un proyecto), para que ambas muestren la misma
// información en vez de tener cada una su propia copia hardcodeada.

// Debe coincidir con los proyectos de col-proyectos.js (misProyectos) —
// son la misma fuente: los proyectos con asignación del colaborador.
const projectForums = [
    { id: "proyectoA", nombre: "Proyecto A", posts: 12, ultimaActividad: "hace 20 min" },
    { id: "proyectoB", nombre: "Proyecto B", posts: 0, ultimaActividad: "sin actividad" },
    { id: "proyectoC", nombre: "Proyecto C", posts: 5, ultimaActividad: "ayer" },
    { id: "proyectoD", nombre: "Proyecto D", posts: 0, ultimaActividad: "sin actividad" },
    { id: "proyectoE", nombre: "Proyecto E", posts: 0, ultimaActividad: "sin actividad" },
    { id: "proyectoG", nombre: "Proyecto G", posts: 0, ultimaActividad: "sin actividad" }
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
