package com.pucp.skillb_ia.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

public class ActivarCuentaForm {

    private String token;

    @NotBlank(message = "Ingresa tu nombre completo.")
    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres.")
    private String nombreCompleto;

    @NotBlank(message = "El teléfono es obligatorio.")
    @Pattern(regexp = PoliticaTelefono.REGEX, message = PoliticaTelefono.MENSAJE)
    private String telefono;

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres.")
    private String descripcion;

    // Solo se exige para colaboradores (se valida en el controlador, que conoce el rol del enlace).
    private MultipartFile cv;

    // Experiencia profesional del pre-registro (solo colaboradores, opcional): las filas vacías se ignoran.
    private List<ExperienciaForm> experiencias = new ArrayList<>();

    // Certificados de habilidades y formación académica (solo colaboradores, opcionales).
    private List<CertificadoPreRegistroForm> certificados = new ArrayList<>();
    private List<EstudioPreRegistroForm> estudios = new ArrayList<>();

    @NotBlank(message = "La contraseña es obligatoria.")
    @Pattern(regexp = PoliticaPassword.REGEX, message = PoliticaPassword.MENSAJE)
    private String password;

    @NotBlank(message = "Confirma tu contraseña.")
    private String confirmarPassword;

    @AssertTrue(message = "Debes aceptar la política para continuar.")
    private boolean aceptaPolitica;

    @AssertTrue(message = "Las contraseñas no coinciden.")
    public boolean isPasswordsCoinciden() {
        return password == null || confirmarPassword == null || password.equals(confirmarPassword);
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public List<ExperienciaForm> getExperiencias() { return experiencias; }
    public void setExperiencias(List<ExperienciaForm> experiencias) { this.experiencias = experiencias; }
    public List<CertificadoPreRegistroForm> getCertificados() { return certificados; }
    public void setCertificados(List<CertificadoPreRegistroForm> certificados) { this.certificados = certificados; }
    public List<EstudioPreRegistroForm> getEstudios() { return estudios; }
    public void setEstudios(List<EstudioPreRegistroForm> estudios) { this.estudios = estudios; }
    public MultipartFile getCv() { return cv; }
    public void setCv(MultipartFile cv) { this.cv = cv; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmarPassword() { return confirmarPassword; }
    public void setConfirmarPassword(String confirmarPassword) { this.confirmarPassword = confirmarPassword; }
    public boolean isAceptaPolitica() { return aceptaPolitica; }
    public void setAceptaPolitica(boolean aceptaPolitica) { this.aceptaPolitica = aceptaPolitica; }
}
