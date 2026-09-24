package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Usuario;

public class PmPerfilView {

    private final Usuario usuario;
    private final String nombreCompleto;
    private final String iniciales;

    public PmPerfilView(Usuario usuario) {
        this.usuario = usuario;
        this.nombreCompleto = usuario.getNombre() + " " + usuario.getApellido();
        String n = (usuario.getNombre() != null && !usuario.getNombre().isEmpty())
                ? String.valueOf(usuario.getNombre().charAt(0)) : "";
        String a = (usuario.getApellido() != null && !usuario.getApellido().isEmpty())
                ? String.valueOf(usuario.getApellido().charAt(0)) : "";
        this.iniciales = (n + a).toUpperCase();
    }

    public Usuario getUsuario() { return usuario; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getIniciales() { return iniciales; }
    public String getCorreo() { return usuario.getCorreo(); }
    public String getCargo() { return usuario.getCargo() != null ? usuario.getCargo().getNombre() : "Project Manager"; }
    public String getFotoUrl() { return usuario.getFotoUrl(); }
    public boolean isTieneFoto() { return usuario.getFotoUrl() != null && !usuario.getFotoUrl().isBlank(); }
}
