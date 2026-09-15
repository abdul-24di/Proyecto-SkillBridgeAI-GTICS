const aiForm = document.getElementById("aiForm");
const aiInput = document.getElementById("aiInput");
const aiMessages = document.getElementById("aiMessages");
const quickButtons = [...document.querySelectorAll("[data-prompt]")];


const mockReplies = [
    { match: ["pendiente", "actividades"], text: "Tienes 4 actividades pendientes esta semana. La más próxima a vencer es \"Implementar login\" en Proyecto A, con fecha límite el 10 de septiembre." },
    { match: ["priorizar", "prioridad"], text: "Te recomiendo priorizar \"Configurar BD\" en Proyecto A: está más cerca de su fecha límite y tiene menos avance registrado que las demás." },
    { match: ["perfil", "mejorar"], text: "Tu perfil está al 78%. Te falta agregar una descripción, registrar una experiencia y sumar una habilidad más para completarlo." },
    { match: ["horas", "trabajad"], text: "Este mes llevas horas registradas por las tareas entregadas en tus proyectos activos. Puedes ver el detalle exacto cuando el módulo de horas esté disponible." },
    { match: ["curso", "capacitaci", "nivel"], text: "Según tus habilidades actuales, un curso de Spring Security o de Arquitectura de Microservicios te ayudaría a avanzar hacia el nivel Semi-Senior." }
];

function pickReply(text) {
    const lower = text.toLowerCase();
    const found = mockReplies.find(r => r.match.some(k => lower.includes(k)));
    return found ? found.text : "Puedo ayudarte con tus actividades, tu perfil profesional o tus proyectos activos. Cuéntame un poco más sobre lo que necesitas.";
}

function appendMessage(text, own) {
    const wrap = document.createElement("div");
    wrap.className = own ? "ai-message own" : "ai-message";
    wrap.innerHTML = own
        ? `<div class="ai-bubble"></div><span class="avatar avatar-sm bg-blue text-white">PK</span>`
        : `<div class="assistant-logo small-logo">AI</div><div class="ai-bubble"></div>`;
    wrap.querySelector(".ai-bubble").textContent = text;
    aiMessages.appendChild(wrap);
    aiMessages.scrollTop = aiMessages.scrollHeight;
}

function send(text) {
    if (!text.trim()) return;
    appendMessage(text, true);
    setTimeout(() => appendMessage(pickReply(text), false), 350);
}

aiForm.addEventListener("submit", (e) => {
    e.preventDefault();
    const text = aiInput.value.trim();
    if (!text) return;
    send(text);
    aiInput.value = "";
});

quickButtons.forEach(btn => btn.addEventListener("click", () => send(btn.dataset.prompt)));