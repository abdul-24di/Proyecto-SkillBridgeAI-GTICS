const changePasswordModal = document.getElementById("changePasswordModal");
const currentPasswordInput = document.getElementById("currentPasswordInput");
const newPasswordInput = document.getElementById("newPasswordInput");
const confirmPasswordInput = document.getElementById("confirmPasswordInput");
const passwordChangeError = document.getElementById("passwordChangeError");

changePasswordModal?.addEventListener("show.bs.modal", () => {
    [currentPasswordInput, newPasswordInput, confirmPasswordInput].forEach(i => i.value = "");
    passwordChangeError.classList.add("d-none");
});

document.getElementById("savePasswordBtn")?.addEventListener("click", () => {
    const current = currentPasswordInput.value;
    const next = newPasswordInput.value;
    const confirm = confirmPasswordInput.value;

    if (!current || !next || !confirm) {
        passwordChangeError.textContent = "Completa los tres campos para continuar.";
        passwordChangeError.classList.remove("d-none");
        return;
    }

    if (next.length < 8) {
        passwordChangeError.textContent = "La nueva contraseña debe tener al menos 8 caracteres.";
        passwordChangeError.classList.remove("d-none");
        return;
    }

    if (next !== confirm) {
        passwordChangeError.textContent = "La nueva contraseña y su confirmación no coinciden.";
        passwordChangeError.classList.remove("d-none");
        return;
    }

    passwordChangeError.classList.add("d-none");
    const Modal = window.bootstrap?.Modal || window.tabler?.bootstrap?.Modal;
    Modal.getOrCreateInstance(changePasswordModal).hide();
});

document.getElementById("logoutBtn")?.addEventListener("click", () => {
    window.location.href = "../auth/login-01.html";
});