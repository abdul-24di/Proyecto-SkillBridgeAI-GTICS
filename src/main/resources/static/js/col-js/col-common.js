/* Comportamiento compartido: panel de notificaciones */
const notifBellBtn = document.getElementById("notifBellBtn");
const notifPanel = document.getElementById("notifPanel");
const notifCloseBtn = document.getElementById("notifCloseBtn");
const notifDot = document.getElementById("notifDot");
const notifMarkAllBtn = document.getElementById("notifMarkAllBtn");

notifBellBtn?.addEventListener("click", (e) => {
    e.stopPropagation();
    notifPanel.classList.toggle("show");
});

notifCloseBtn?.addEventListener("click", () => notifPanel.classList.remove("show"));

document.addEventListener("click", (e) => {
    if (notifPanel && notifPanel.classList.contains("show") && !notifPanel.contains(e.target) && e.target !== notifBellBtn) {
        notifPanel.classList.remove("show");
    }
});

notifMarkAllBtn?.addEventListener("click", () => {
    document.querySelectorAll(".notification-item-dot").forEach(dot => {
        dot.classList.remove("bg-red", "bg-green");
        dot.classList.add("bg-secondary");
    });
    notifDot?.remove();
});