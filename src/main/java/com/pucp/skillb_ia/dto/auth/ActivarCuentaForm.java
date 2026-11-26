package com.pucp.skillb_ia.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/** Formulario de activación de cuenta. Los colaboradores completan además su pre-registro. */
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

    // Solo colaboradores: los certificados de habilidades son opcionales; la formación académica, al menos una ya
    // concluida, salvo que marque que todavía no tiene.
    private boolean sinEstudios;
    private List<CertificadoPreRegistroForm> certificados = new ArrayList<>();
    private List<EstudioPreRegistroForm> estudios = new ArrayList<>();

    @NotBlank(message = "La contraseña es obligatoria.")
    @Pattern(regexp = PoliticaPassword.REGEX, message = PoliticaPassword.MENSAJE)
    private String password;

    @NotBlank(message = "Confirma tu contraseña.")
    private String confirmarPassword;

    /**
     * Comprueba que las dos contraseñas sean iguales.
     *
     * @return {@code true} si coinciden o falta alguna
     */
    @AssertTrue(message = "Las contraseñas no coinciden.")
    public boolean isPasswordsCoinciden() {
        return password == null || confirmarPassword == null || password.equals(confirmarPassword);
    }

    /**
     * Devuelve {@code token}.
     *
     * @return el valor de {@code token}
     */
    public String getToken() { return token; }
    /**
     * Asigna {@code token}.
     *
     * @param token token del enlace de activación
     */
    public void setToken(String token) { this.token = token; }
    /**
     * Devuelve {@code nombres}.
     *
     * @return el valor de {@code nombres}
     */
    public String getNombres() { return nombres; }
    /**
     * Asigna {@code nombres}.
     *
     * @param nombres nombres
     */
    public void setNombres(String nombres) { this.nombres = nombres; }
    /**
     * Devuelve {@code apellidos}.
     *
     * @return el valor de {@code apellidos}
     */
    public String getApellidos() { return apellidos; }
    /**
     * Asigna {@code apellidos}.
     *
     * @param apellidos apellidos
     */
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    /**
     * Une los nombres y los apellidos.
     * <p>
     * Como figura en el DNI, para compararlo con la consulta.
     *
     * @return nombre completo
     */
    public String nombreCompleto() { return nombres + " " + apellidos; }

    /**
     * Devuelve {@code dni}.
     *
     * @return el valor de {@code dni}
     */
    public String getDni() { return dni; }
    /**
     * Asigna {@code dni}.
     *
     * @param dni número de DNI
     */
    public void setDni(String dni) { this.dni = dni; }
    /**
     * Devuelve {@code telefono}.
     *
     * @return el valor de {@code telefono}
     */
    public String getTelefono() { return telefono; }
    /**
     * Asigna {@code telefono}.
     *
     * @param telefono teléfono ingresado
     */
    public void setTelefono(String telefono) { this.telefono = telefono; }
    /**
     * Devuelve {@code descripcion}.
     *
     * @return el valor de {@code descripcion}
     */
    public String getDescripcion() { return descripcion; }
    /**
     * Asigna {@code descripcion}.
     *
     * @param descripcion descripción; puede ser {@code null} o vacía
     */
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    /**
     * Devuelve {@code sinExperiencia}.
     *
     * @return {@code true} si se cumple
     */
    public boolean isSinExperiencia() { return sinExperiencia; }
    /**
     * Asigna {@code sinExperiencia}.
     *
     * @param sinExperiencia {@code true} si marcó que no tiene experiencia
     */
    public void setSinExperiencia(boolean sinExperiencia) { this.sinExperiencia = sinExperiencia; }
    /**
     * Devuelve {@code sinEstudios}.
     *
     * @return {@code true} si se cumple
     */
    public boolean isSinEstudios() { return sinEstudios; }
    /**
     * Asigna {@code sinEstudios}.
     *
     * @param sinEstudios {@code true} si marcó que no tiene estudios concluidos
     */
    public void setSinEstudios(boolean sinEstudios) { this.sinEstudios = sinEstudios; }
    /**
     * Devuelve {@code experiencias}.
     *
     * @return el valor de {@code experiencias}
     */
    public List<ExperienciaForm> getExperiencias() { return experiencias; }
    /**
     * Asigna {@code experiencias}.
     *
     * @param experiencias filas de experiencia
     */
    public void setExperiencias(List<ExperienciaForm> experiencias) { this.experiencias = experiencias; }
    /**
     * Devuelve {@code certificados}.
     *
     * @return el valor de {@code certificados}
     */
    public List<CertificadoPreRegistroForm> getCertificados() { return certificados; }
    /**
     * Asigna {@code certificados}.
     *
     * @param certificados filas de certificados
     */
    public void setCertificados(List<CertificadoPreRegistroForm> certificados) { this.certificados = certificados; }
    /**
     * Devuelve {@code estudios}.
     *
     * @return el valor de {@code estudios}
     */
    public List<EstudioPreRegistroForm> getEstudios() { return estudios; }
    /**
     * Asigna {@code estudios}.
     *
     * @param estudios filas de formación
     */
    public void setEstudios(List<EstudioPreRegistroForm> estudios) { this.estudios = estudios; }
    /**
     * Devuelve {@code cv}.
     *
     * @return el valor de {@code cv}
     */
    public MultipartFile getCv() { return cv; }
    /**
     * Asigna {@code cv}.
     *
     * @param cv CV en PDF
     */
    public void setCv(MultipartFile cv) { this.cv = cv; }
    /**
     * Devuelve {@code cvGuardado}.
     *
     * @return el valor de {@code cvGuardado}
     */
    public String getCvGuardado() { return cvGuardado; }
    /**
     * Asigna {@code cvGuardado}.
     *
     * @param cvGuardado id del CV ya recibido
     */
    public void setCvGuardado(String cvGuardado) { this.cvGuardado = cvGuardado; }
    /**
     * Devuelve {@code password}.
     *
     * @return el valor de {@code password}
     */
    public String getPassword() { return password; }
    /**
     * Asigna {@code password}.
     *
     * @param password contraseña en texto plano; se guarda cifrada con BCrypt
     */
    public void setPassword(String password) { this.password = password; }
    /**
     * Devuelve {@code confirmarPassword}.
     *
     * @return el valor de {@code confirmarPassword}
     */
    public String getConfirmarPassword() { return confirmarPassword; }
    /**
     * Asigna {@code confirmarPassword}.
     *
     * @param confirmarPassword repetición de la contraseña
     */
    public void setConfirmarPassword(String confirmarPassword) { this.confirmarPassword = confirmarPassword; }
}
