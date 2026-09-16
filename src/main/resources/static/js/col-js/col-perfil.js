// ---------------- MODAL: Agregar formación ----------------
const eduCurrentInput = document.getElementById("eduCurrentInput");
const eduEndInput = document.getElementById("eduEndInput");

eduCurrentInput?.addEventListener("change", () => {
    eduEndInput.disabled = eduCurrentInput.checked;
    if (eduCurrentInput.checked) eduEndInput.value = "";
});

// ---------------- MODAL: Agregar habilidad (crear una nueva si no está en el catálogo) ----------------
const skillCatalogWrap = document.getElementById("skillCatalogWrap");
const skillNewWrap = document.getElementById("skillNewWrap");
const skillCatalogSelect = document.getElementById("skillCatalogSelect");
const skillNewNameInput = document.getElementById("skillNewNameInput");
const skillNewCategorySelect = document.getElementById("skillNewCategorySelect");
const toggleNewSkillLink = document.getElementById("toggleNewSkillLink");

let creandoNuevaHabilidad = false;

toggleNewSkillLink?.addEventListener("click", (evento) => {
    evento.preventDefault();
    creandoNuevaHabilidad = !creandoNuevaHabilidad;

    if (creandoNuevaHabilidad) {
        skillCatalogWrap.style.display = "none";
        skillNewWrap.style.display = "block";
        skillCatalogSelect.value = "";
        toggleNewSkillLink.textContent = "Elegir del catálogo en vez de crear una nueva";
    } else {
        skillCatalogWrap.style.display = "block";
        skillNewWrap.style.display = "none";
        skillNewNameInput.value = "";
        toggleNewSkillLink.textContent = "¿No encuentras tu habilidad? Créala aquí";
    }
});