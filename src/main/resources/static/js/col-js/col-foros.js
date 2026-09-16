const views = {
    comunidad: document.getElementById("comunidadView"),
    proyectos: document.getElementById("proyectosView")
};
const scopeButtons = [...document.querySelectorAll("[data-scope]")];

scopeButtons.forEach(btn => btn.addEventListener("click", () => {
    scopeButtons.forEach(b => b.classList.remove("active"));
    btn.classList.add("active");
    Object.values(views).forEach(v => v && v.classList.add("d-none"));
    const target = views[btn.dataset.scope];
    if (target) target.classList.remove("d-none");
}));