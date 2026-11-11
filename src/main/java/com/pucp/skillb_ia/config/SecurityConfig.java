package com.pucp.skillb_ia.config;

import com.pucp.skillb_ia.security.LimiteSolicitudesFilter;
import com.pucp.skillb_ia.security.RegistroAccesoDenegadoHandler;
import com.pucp.skillb_ia.security.RegistroLoginFallidoHandler;
import com.pucp.skillb_ia.security.RoleRedirectSuccessHandler;
import com.pucp.skillb_ia.security.SesionActivaLoginFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;


@Configuration
public class SecurityConfig {

    // Peticiones por IP y por minuto (ver LimiteSolicitudesFilter).
    @Value("${app.limite.peticiones-por-minuto:300}")
    private int maxPeticionesPorMinuto;

    @Value("${app.limite.formularios-por-minuto:20}")
    private int maxFormulariosPorMinuto;

    // Rutas de la API REST (chat del PM, chat del colaborador y notificaciones).
    private static final RequestMatcher RUTAS_API = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/pm/api/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/colaborador/api/**"));

    private final AuthenticationEntryPoint sinSesionApi = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
    private final AuthenticationEntryPoint irAlLogin = new LoginUrlAuthenticationEntryPoint("/login");

    // BCrypt para password_hash — lo usa AuthService al activar la cuenta (A6)
    // y al restablecer la contraseña.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RoleRedirectSuccessHandler successHandler,
                                           RegistroAccesoDenegadoHandler accesoDenegadoHandler,
                                           RegistroLoginFallidoHandler loginFallidoHandler) throws Exception {
        http
            // Va primero: una IP que inunda el sitio se corta antes de leer la sesión en la base de datos.
            .addFilterBefore(new LimiteSolicitudesFilter(maxPeticionesPorMinuto, maxFormulariosPorMinuto),
                    SecurityContextHolderFilter.class)
            // Con sesión activa, /login lleva al panel del rol: no se puede iniciar otra sesión encima.
            .addFilterBefore(new SesionActivaLoginFilter(), UsernamePasswordAuthenticationFilter.class)
            // Rol equivocado: se registra el intento en la auditoría y se muestra la página 403 propia.
            .exceptionHandling(excepciones -> excepciones
                    .accessDeniedHandler(accesoDenegadoHandler)
                    // Sin sesión: la API REST (JSON) responde 401 y todo lo demás sigue redirigiendo al login.
                    .authenticationEntryPoint((request, response, ex) -> {
                        if (RUTAS_API.matches(request)) {
                            sinSesionApi.commence(request, response, ex);
                        } else {
                            irAlLogin.commence(request, response, ex);
                        }
                    }))
            .authorizeHttpRequests(auth -> auth
                // Las páginas de error (403, 404, 429, 500...) deben verse también sin sesión.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                // /uploads/** (CV, certificados, fotos, evidencias) no es público: exige sesión, por la
                // regla anyRequest().authenticated() de abajo.
                .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico", "/tabler/**", "/documentos/**", "/plantillas/**").permitAll()
                .requestMatchers("/", "/login", "/login.html").permitAll()
                .requestMatchers("/activar-cuenta", "/activar-cuenta.html").permitAll()
                .requestMatchers("/recuperar", "/recuperar-contrasena.html").permitAll()
                .requestMatchers("/verificar-codigo", "/verificar-codigo.html").permitAll()
                .requestMatchers("/nueva-contrasena", "/nueva-contrasena.html").permitAll()
                .requestMatchers("/contrasena-actualizada", "/contrasena-actualizada.html").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/pm/**").hasRole("PROJECT_MANAGER")
                .requestMatchers("/rm/**").hasRole("RESOURCE_MANAGER")
                .requestMatchers("/colaborador/**").hasRole("COLABORADOR")
                .anyRequest().authenticated()
            )
            // CSRF activo (configuración por defecto de Spring Security): todo POST debe traer el token.
            // Los <form th:action> lo reciben solos; los formularios cuyo action pone el JS lo llevan en un
            // campo oculto, y los fetch lo mandan en una cabecera con /js/csrf.js. Por eso el logout es POST.

            // Permite abrir evidencias propias (PDF, TXT) en el iframe del visor; sigue bloqueando sitios externos
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                // Solo reglas que no afectan a los scripts de las vistas: nadie externo nos embebe, no hay
                // plugins, y un HTML inyectado no puede cambiar la base de los enlaces ni enviar formularios fuera.
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "frame-ancestors 'self'; object-src 'none'; base-uri 'self'; form-action 'self'"))
                // Los enlaces de activación llevan un token en la URL: no se envía a sitios externos.
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)))

            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureHandler(loginFallidoHandler)
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );
        return http.build();
    }
}
