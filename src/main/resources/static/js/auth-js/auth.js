

/* Mostrar / ocultar contraseña en cualquier input marcado */
document.querySelectorAll("[data-toggle-password]").forEach(btn => {
    const input = document.getElementById(btn.dataset.togglePassword);
    if (!input) return;
    btn.addEventListener("click", () => {
        const showing = input.type === "text";
        input.type = showing ? "password" : "text";
        btn.innerHTML = showing
            ? `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>`
            : `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.94 10.94 0 0 1 12 20c-7 0-11-8-11-8a21.6 21.6 0 0 1 5.06-6.06M9.9 4.24A10.6 10.6 0 0 1 12 4c7 0 11 8 11 8a21.7 21.7 0 0 1-3.22 4.44M14.12 14.12a3 3 0 1 1-4.24-4.24"/><path d="M1 1l22 22"/></svg>`;
    });
});

/* ---------- Registro ---------- */
const registerForm = document.getElementById("registerForm");
registerForm?.addEventListener("submit", e => {
    e.preventDefault();
    const box = document.getElementById("registerAlertBox");

    const password = document.getElementById("regPassword").value;
    const confirm = document.getElementById("regConfirmPassword").value;

    if (password.length < 6) {
        box.innerHTML = `<div class="auth-alert error">La contraseña debe tener al menos 6 caracteres.</div>`;
        return;
    }
    if (password !== confirm) {
        box.innerHTML = `<div class="auth-alert error">Las contraseñas no coinciden.</div>`;
        return;
    }

    box.innerHTML
    setTimeout(() => { window.location.href = registerForm.dataset.redirect; }, 900);
});

/* ---------- Recuperar contraseña ---------- */
const forgotForm = document.getElementById("forgotForm");
forgotForm?.addEventListener("submit", e => {
    e.preventDefault();
    const box = document.getElementById("forgotAlertBox");
    const btn = document.getElementById("forgotSubmitBtn");
    box.innerHTML = `<div class="auth-alert success">Si el correo existe en SkillBridge AI, te enviamos un enlace de recuperación.</div>`;
    btn.disabled = true;
    btn.textContent = "Enlace enviado";
});

/* ---------- Nueva contraseña ---------- */
const resetForm = document.getElementById("resetForm");
resetForm?.addEventListener("submit", e => {
    e.preventDefault();
    const box = document.getElementById("resetAlertBox");
    const password = document.getElementById("newPassword").value;
    const confirm = document.getElementById("confirmNewPassword").value;

    if (password.length < 6) {
        box.innerHTML = `<div class="auth-alert error">La contraseña debe tener al menos 6 caracteres.</div>`;
        return;
    }
    if (password !== confirm) {
        box.innerHTML = `<div class="auth-alert error">Las contraseñas no coinciden.</div>`;
        return;
    }

    window.location.href = resetForm.dataset.redirect;
});