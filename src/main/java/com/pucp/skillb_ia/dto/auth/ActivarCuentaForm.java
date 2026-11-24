package com.pucp.skillb_ia.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

public class ActivarCuentaForm {

    private static final String PATRON_NOMBRE = "^[\\p{L}][\\p{L} .'-]*$";

    private String token;

    // Nombres y apellidos por separado: antes era un solo campo y se cortaba en el primer espacio, así que el segundo
    // nombre quedaba como apellido.
    @NotBlank(message = "Ingresa tus nombres.")
    @Size(min = 2, max = 150, message = "Los nombres deben tener entre 2 y 150 caracteres.")
    @Pattern(regexp = PATRON_NOMBRE, message = "Los nombres solo pueden tener letras, espacios, puntos, apóstrofes y guiones.")
    private String nombres;

    @NotBlank(message = "Ingresa tus apellidos.")
    @Size(min = 2, max = 150, message = "Los apellidos deben tener entre 2 y 150 caracteres.")
    @Pattern(regexp = PATRON_NOMBRE, message = "Los apellidos solo pueden tener letras, espacios, puntos, apóstrofes y guiones.")
    private String apellidos;

    @NotBlank(message = "El teléfono es obligatorio.")
    @Pattern(regexp = PoliticaTelefono.REGEX, message = PoliticaTelefono.MENSAJE)
    private String telefono;

    // DNI de todos los roles. El formato se valida aquí; que no se repita y la consulta a la API, en DniService.
    @NotBlank(message = "Ingresa tu DNI.")
    @Pattern(regexp = "^[0-9]{8}$", message = "El DNI debe tener exactamente 8 dígitos, sin espacios ni letras.")
    private String dni;

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres.")
    private String descripcion;

    // Solo se exige para colaboradores (se valida en el controlador, que conoce el rol del enlace).
    private MultipartFile cv;

    // Id del CV que ya se recibió en un envío anterior (ver PreRegistroArchivosService).
    private String cvGuardado;

    // Experiencia profesional del pre-registro (solo colaboradores): al menos una, salvo que marque que no tiene.
    // Las filas vacías se ignoran.
    private boolean sinExperiencia;
    private List<ExperienciaForm> experiencias = new ArrayList<>();

    // Solo colaboradores: los certificados de habilidades son opcionales; la formación académica, al menos una.
    private List<CertificadoPreRegistroForm> certificados = new ArrayList<>();
    private List<EstudioPreRegistroForm> estudios = new ArrayList<>();

    @NotBlank(message = "La contraseña es obligatoria.")
    @Pattern(regexp = PoliticaPassword.REGEX, message = PoliticaPassword.MENSAJE)
    private String password;

    @NotBlank(message = "Confirma tu contraseña.")
    private String confirmarPassword;

    @AssertTrue(message = "Las contraseñas no coinciden.")
    public boolean isPasswordsCoinciden() {
        return password == null || confirmarPassword == null || password.equals(confirmarPassword);
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    // Como figura en el DNI, para compararlo con la consulta.
    public String nombreCompleto() { return nombres + " " + apellidos; }

    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public boolean isSinExperiencia() { return sinExperiencia; }
    public void setSinExperiencia(boolean sinExperiencia) { this.sinExperiencia = sinExperiencia; }
    public List<ExperienciaForm> getExperiencias() { return experiencias; }
    public void setExperiencias(List<ExperienciaForm> experiencias) { this.experiencias = experiencias; }
    public List<CertificadoPreRegistroForm> getCertificados() { return certificados; }
    public void setCertificados(List<CertificadoPreRegistroForm> certificados) { this.certificados = certificados; }
    public List<EstudioPreRegistroForm> getEstudios() { return estudios; }
    public void setEstudios(List<EstudioPreRegistroForm> estudios) { this.estudios = estudios; }
    public MultipartFile getCv() { return cv; }
    public void setCv(MultipartFile cv) { this.cv = cv; }
    public String getCvGuardado() { return cvGuardado; }
    public void setCvGuardado(String cvGuardado) { this.cvGuardado = cvGuardado; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmarPassword() { return confirmarPassword; }
    public void setConfirmarPassword(String confirmarPassword) { this.confirmarPassword = confirmarPassword; }
}
