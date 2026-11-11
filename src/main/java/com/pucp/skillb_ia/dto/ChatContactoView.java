package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Usuario;

public class ChatContactoView {
    private final Long id;
    private final String nombre;
    private final String iniciales;
    private final String rol;

    public ChatContactoView(Usuario usuario, String rol) {
        this.id = usuario.getId();
        this.nombre = usuario.getNombre() + " " + usuario.getApellido();
        String n = (usuario.getNombre() != null && !usuario.getNombre().isEmpty()) ? usuario.getNombre().substring(0, 1) : "";
        String a = (usuario.getApellido() != null && !usuario.getApellido().isEmpty()) ? usuario.getApellido().substring(0, 1) : "";
        this.iniciales = (n + a).toUpperCase();
        this.rol = rol;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getIniciales() { return iniciales; }
    public String getRol() { return rol; }
}