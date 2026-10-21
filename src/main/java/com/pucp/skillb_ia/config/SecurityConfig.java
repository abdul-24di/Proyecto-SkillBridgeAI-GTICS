package com.pucp.skillb_ia.config;

import com.pucp.skillb_ia.security.RegistroAccesoDenegadoHandler;
import com.pucp.skillb_ia.security.RoleRedirectSuccessHandler;
import com.pucp.skillb_ia.security.SesionActivaLoginFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Épica 2 — Historia de Inicio de sesión + Historia de Gestión de sesión.
// El formulario de auth/login.html ya usa los nombres por defecto de Spring
// Security (username/password/remember-me), así que se integra directo con
// formLogin() en vez de manejar la sesión a mano.
//
// La Historia de "Control de acceso según rol" (restringir cada ruta /admin,
// /pm, /rm, /colaborador según el rol autenticado) todavía no está
// implementada aquí a propósito — es la siguiente historia de esta misma
// épica, y requiere revisar cada ruta existente antes de bloquear nada.
// Por ahora todo queda accesible para no romper las vistas ya construidas.
@Configuration
public class SecurityConfig {

    // BCrypt para password_hash — lo usa AuthService al activar la cuenta (A6)
    // y al restablecer la contraseña.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RoleRedirectSuccessHandler successHandler,
                                           RegistroAccesoDenegadoHandler accesoDenegadoHandler) throws Exception {
        http
            // Con sesión activa, /login lleva al panel del rol: no se puede iniciar otra sesión encima.
            .addFilterBefore(new SesionActivaLoginFilter(), UsernamePasswordAuthenticationFilter.class)
            // Rol equivocado: se registra el intento en la auditoría y se muestra la página 403 propia.
            .exceptionHandling(excepciones -> excepciones.accessDeniedHandler(accesoDenegadoHandler))
            .authorizeHttpRequests(auth -> auth
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
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))

            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureUrl("/login?error")
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )

            .rememberMe(remember -> remember
                .key("skillbridge-remember-me")
                .rememberMeParameter("remember-me")
            );
        return http.build();
    }
}
