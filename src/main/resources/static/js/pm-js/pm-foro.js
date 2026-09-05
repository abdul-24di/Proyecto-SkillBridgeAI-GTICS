const posts = [
  { id: 1, tag: "Bug", title: "Error de conexión en base de datos al subir imagen", excerpt: "Cuando intento subir una imagen mayor a 5MB, el endpoint devuelve timeout.", author: "Ana Torres", ago: "hace 2 horas", order: 1, baseVotes: 15, userVote: 0, replies: 4, answerVotes: 6, answerUserVote: 0 },
  { id: 2, tag: "Idea", title: "¿Deberíamos usar Redis para el caché?", excerpt: "Actualmente manejamos caché en memoria con Spring Cache. ¿Conviene migrar a Redis?", author: "Luis Paredes", ago: "hace 5 horas", order: 2, baseVotes: 22, userVote: 0, replies: 8, answerVotes: 10, answerUserVote: 0 },
  { id: 3, tag: "Pregunta", title: "¿Cuál es el flujo correcto para dar de baja un ítem?", excerpt: "Necesito confirmar el flujo funcional y el historial de movimientos.", author: "Pedro Díaz", ago: "hace 1 día", order: 3, baseVotes: 9, userVote: 0, replies: 3, answerVotes: 4, answerUserVote: 0 },
  { id: 4, tag: "Anuncio", title: "Reunión de sprint review este viernes", excerpt: "Recordatorio: la sprint review será este viernes a las 10:00 AM.", author: "Abdon Vallejo", ago: "hace 1 día", order: 4, baseVotes: 7, userVote: 0, replies: 2, answerVotes: 3, answerUserVote: 0 },
  { id: 5, tag: "Bug", title: "El dashboard no actualiza métricas en tiempo real", excerpt: "El widget de stock total se queda congelado.", author: "María Ruiz", ago: "hace 2 días", order: 5, baseVotes: 11, userVote: 0, replies: 5, answerVotes: 5, answerUserVote: 0 }
];

const box = document.getElementById("forumPosts");
const tagButtons = [...document.querySelectorAll("[data-tag]")];
const sortSelect = document.getElementById("forumSort");
let activeTag = "all";

function tagClass(tag) {
  return { Bug: "bg-red-lt", Idea: "bg-purple-lt", Pregunta: "bg-orange-lt", Anuncio: "bg-blue-lt" }[tag] || "bg-secondary-lt";
}

function totalVotes(post) {
  return post.baseVotes + post.userVote;
}

function render() {
  let filtered = activeTag === "all" ? [...posts] : posts.filter(post => post.tag === activeTag);
  filtered.sort(sortSelect.value === "votes"
    ? (a, b) => totalVotes(b) - totalVotes(a)
    : (a, b) => a.order - b.order);

  box.innerHTML = filtered.map(post => `<article class="card forum-post"><div class="forum-post-body"><div class="vote-box"><button type="button" class="vote-button ${post.userVote === 1 ? "active" : ""}" data-vote="1" data-vote-target="post" data-post-id="${post.id}" aria-label="Votar publicación a favor">↑</button><div class="vote-count">${totalVotes(post)}</div><button type="button" class="vote-button down ${post.userVote === -1 ? "active" : ""}" data-vote="-1" data-vote-target="post" data-post-id="${post.id}" aria-label="Votar publicación en contra">↓</button></div><div><span class="badge ${tagClass(post.tag)}">${post.tag}</span><div class="post-title mt-1">${post.title}</div><div class="post-excerpt">${post.excerpt}</div><div class="post-meta">Publicado por ${post.author} · ${post.ago}</div><div class="featured-answer"><span>Respuesta destacada</span><div class="answer-votes"><button type="button" class="vote-button ${post.answerUserVote === 1 ? "active" : ""}" data-vote="1" data-vote-target="answer" data-post-id="${post.id}" aria-label="Votar respuesta a favor">↑</button><strong>${post.answerVotes + post.answerUserVote}</strong><button type="button" class="vote-button down ${post.answerUserVote === -1 ? "active" : ""}" data-vote="-1" data-vote-target="answer" data-post-id="${post.id}" aria-label="Votar respuesta en contra">↓</button></div></div></div><div class="reply-count"><strong>${post.replies}</strong>respuestas</div></div></article>`).join("");
}

tagButtons.forEach(button => button.addEventListener("click", () => {
  activeTag = button.dataset.tag;
  tagButtons.forEach(item => {
    item.classList.remove("btn-primary");
    item.classList.add("btn-outline-secondary");
  });
  button.classList.remove("btn-outline-secondary");
  button.classList.add("btn-primary");
  render();
}));

box.addEventListener("click", event => {
  const voteButton = event.target.closest("[data-vote]");
  if (!voteButton) return;
  const post = posts.find(item => item.id === Number(voteButton.dataset.postId));
  const requestedVote = Number(voteButton.dataset.vote);
  if (voteButton.dataset.voteTarget === "answer") {
    post.answerUserVote = post.answerUserVote === requestedVote ? 0 : requestedVote;
  } else {
    post.userVote = post.userVote === requestedVote ? 0 : requestedVote;
  }
  render();
});

sortSelect.addEventListener("change", render);
render();
