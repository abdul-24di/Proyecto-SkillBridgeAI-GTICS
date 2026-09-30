package com.pucp.skillb_ia.config;

import com.pucp.skillb_ia.security.RoleRedirectSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
    public SecurityFilterChain filterChain(HttpSecurity http, RoleRedirectSuccessHandler successHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/img/**", "/uploads/**", "/favicon.ico", "/tabler/**", "/documentos/**", "/plantillas/**").permitAll()
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
            // Simplificación deliberada: los formularios existentes (auth, admin,
            // etc.) todavía no llevan el campo oculto de token CSRF. Retomar esto
            // cuando se agregue CSRF a todos los forms del proyecto, no solo a Auth.
            .csrf(csrf -> csrf.disable())

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
