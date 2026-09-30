
/* ---------- Validación propia (sin el globo nativo del navegador) ---------- */
/* Los forms marcados con data-custom-validate llevan novalidate: seguimos
   usando required/minlength/pattern en el HTML (checkValidity() los sigue
   evaluando), pero el mensaje de error se muestra con nuestro propio estilo
   en el <div class="auth-field-error" data-error-for="idDelCampo">. */
document.querySelectorAll("form[data-custom-validate]").forEach(form => {
    const campos = Array.from(form.querySelectorAll("[required], [pattern], [minlength]"));

    const errorDe = campo => form.querySelector(`[data-error-for="${campo.id}"]`);
    const limpiar = campo => {
        errorDe(campo)?.classList.remove("is-visible");
        campo.closest(".auth-input-icon")?.classList.remove("has-error");
    };
    const marcar = campo => {
        errorDe(campo)?.classList.add("is-visible");
        campo.closest(".auth-input-icon")?.classList.add("has-error");
    };

    campos.forEach(campo => {
        campo.addEventListener("input", () => limpiar(campo));
        campo.addEventListener("change", () => limpiar(campo));
    });

    form.addEventListener("submit", e => {
        campos.forEach(limpiar);
        let primerInvalido = null;
        campos.forEach(campo => {
            if (!campo.checkValidity()) {
                marcar(campo);
                primerInvalido = primerInvalido || campo;
            }
        });

        const password = form.querySelector('input[name="password"]');
        const confirmar = form.querySelector('input[name="confirmarPassword"]');
        if (password && confirmar && password.value && confirmar.value && password.value !== confirmar.value) {
            marcar(confirmar);
            primerInvalido = primerInvalido || confirmar;
        }

        if (primerInvalido) {
            e.preventDefault();
            primerInvalido.focus();
        }
    });
});

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

/* ---------- Verificar código: auto-avance + concatenar antes de enviar ---------- */
/* Las validaciones reales (código correcto, vigencia) las hace el servidor —
   este script solo arma la experiencia de escritura y junta los 6 dígitos en
   el input oculto "codigo" antes de que el form haga el POST real. */
const verifyForm = document.getElementById("verifyForm");
if (verifyForm) {
    const codeInputs = Array.from(document.querySelectorAll("#codeRow .auth-code-input"));
    const codigoCompleto = document.getElementById("codigoCompleto");

    codeInputs.forEach((input, i) => {
        input.addEventListener("input", () => {
            input.value = input.value.replace(/[^0-9]/g, "").slice(0, 1);
            if (input.value && i < codeInputs.length - 1) {
                codeInputs[i + 1].focus();
            }
        });
        input.addEventListener("keydown", e => {
            if (e.key === "Backspace" && !input.value && i > 0) {
                codeInputs[i - 1].focus();
            }
        });
        input.addEventListener("paste", e => {
            const pasted = (e.clipboardData || window.clipboardData).getData("text").replace(/[^0-9]/g, "");
            if (!pasted) return;
            e.preventDefault();
            pasted.slice(0, codeInputs.length).split("").forEach((digit, idx) => {
                if (codeInputs[idx]) codeInputs[idx].value = digit;
            });
            const next = codeInputs[Math.min(pasted.length, codeInputs.length - 1)];
            next?.focus();
        });
    });

    verifyForm.addEventListener("submit", e => {
        const codigo = codeInputs.map(i => i.value).join("");
        if (codigo.length < codeInputs.length) {
            e.preventDefault();
            codeInputs[0].focus();
            return;
        }
        codigoCompleto.value = codigo;
    });
}
