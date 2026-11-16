package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.DniService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DNI: formato, que no se repita, consulta a la API (simulada con Mockito, sin gastar consultas reales),
// registro una sola vez y vista en el perfil.
@SpringBootTest
@ActiveProfiles("test")
class DniTests {

    private static final String URL = "https://api.verifica.id/v2/consulta/personas";

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private DniService dniServiceSinClave;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Validación ----------

    @Test
    void rechazaFormatosInvalidosYDniRepetido() {
        for (String malo : new String[]{null, "", "1234567", "123456789", "1234567a", "12 34567"}) {
            assertThatThrownBy(() -> dniServiceSinClave.validar(malo, "Ana Perez"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(DniService.MENSAJE_FORMATO);
        }
        Usuario existente = usuario("con.dni", "COLABORADOR");
        String dni = DniPrueba.unico();
        dniServiceSinClave.registrar(existente, dni, false);

        assertThatThrownBy(() -> dniServiceSinClave.validar(dni, "Ana Perez"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ese DNI ya está registrado en otra cuenta.");
    }

    @Test
    void sinApiKeyElDniSeAceptaComoSinVerificar() {
        assertThat(dniServiceSinClave.validar(DniPrueba.unico(), "Ana Perez")).isFalse();
    }

    @Test
    void conLaApiConfirmaElDniSiElNombreCoincide() {
        RestTemplate api = mock(RestTemplate.class);
        when(api.postForEntity(eq(URL), any(), eq(Map.class))).thenReturn(ResponseEntity.ok(
                Map.of("data", Map.of("nombres", "LUIS ABDUL", "apellido_paterno", "PAREDES", "apellido_materno", "DÍAZ"))));
        DniService conApi = new DniService(usuarioRepository, api, "clave-de-prueba", URL);

        assertThat(conApi.validar(DniPrueba.unico(), "Luis Paredes")).isTrue();
        assertThat(conApi.validar(DniPrueba.unico(), "luis abdul paredes diaz")).isTrue();
        assertThatThrownBy(() -> conApi.validar(DniPrueba.unico(), "Luis Gomez"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no coincide");
        assertThatThrownBy(() -> conApi.validar(DniPrueba.unico(), "Luis"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no coincide");
    }

    @Test
    void siLaApiNoEncuentraElDniSeRechaza() {
        RestTemplate api = mock(RestTemplate.class);
        when(api.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
        DniService conApi = new DniService(usuarioRepository, api, "clave-de-prueba", URL);

        assertThatThrownBy(() -> conApi.validar(DniPrueba.unico(), "Ana Perez"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No encontramos ese DNI. Revisa que esté bien escrito.");
    }

    @Test
    void siLaApiFallaNoSeBloqueaElRegistro() {
        RestTemplate sinSaldo = mock(RestTemplate.class);
        when(sinSaldo.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        RestTemplate caida = mock(RestTemplate.class);
        when(caida.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThat(new DniService(usuarioRepository, sinSaldo, "clave", URL).validar(DniPrueba.unico(), "Ana Perez")).isFalse();
        assertThat(new DniService(usuarioRepository, caida, "clave", URL).validar(DniPrueba.unico(), "Ana Perez")).isFalse();
    }

    // ---------- Registro desde el perfil ----------

    @Test
    void unaCuentaSinDniLoRegistraUnaSolaVezDesdeElPerfil() throws Exception {
        Usuario rm = usuario("rm", "RESOURCE_MANAGER");
        autenticar(rm);
        String dni = DniPrueba.unico();

        mvc.perform(get("/rm/perfil")).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("Registrar DNI"));

        mvc.perform(post("/perfil/dni").param("dni", dni).header("Referer", "http://localhost/rm/perfil"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("dniGuardado"));

        Usuario guardado = usuarioRepository.findById(rm.getId()).orElseThrow();
        assertThat(guardado.getDni()).isEqualTo(dni);
        assertThat(guardado.isDniVerificado()).isFalse();

        mvc.perform(post("/perfil/dni").param("dni", DniPrueba.unico()))
                .andExpect(flash().attribute("dniError", "Tu DNI ya está registrado y no se puede cambiar."));
        assertThat(usuarioRepository.findById(rm.getId()).orElseThrow().getDni()).isEqualTo(dni);

        String html = mvc.perform(get("/rm/perfil")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains(dni).contains("Sin verificar").doesNotContain("Registrar DNI");
    }

    @Test
    void unDniInvalidoDesdeElPerfilMuestraElErrorYNoSeGuarda() throws Exception {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        autenticar(pm);

        mvc.perform(post("/perfil/dni").param("dni", "12AB"))
                .andExpect(flash().attribute("dniError", DniService.MENSAJE_FORMATO))
                .andExpect(flash().attribute("dniIngresado", "12AB"));
        assertThat(usuarioRepository.findById(pm.getId()).orElseThrow().getDni()).isNull();
    }

    // ---------- Utilidades ----------

    private Usuario usuario(String prefijo, String nombreRol) {
        Usuario u = new Usuario();
        u.setCorreo(prefijo + "." + UUID.randomUUID() + "@test.local");
        u.setNombre("Ana");
        u.setApellido("Perez");
        u.setRol(rol(nombreRol));
        u.setActivo(true);
        u.setHorasContratadasSemana(new BigDecimal("40.00"));
        u.setHorasDisponibles(new BigDecimal("40.00"));
        return usuarioRepository.save(u);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombre);
            return rolRepository.save(r);
        });
    }

    private void autenticar(Usuario usuario) {
        UsuarioDetails details = new UsuarioDetails(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
