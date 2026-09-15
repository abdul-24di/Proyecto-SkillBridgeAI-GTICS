package com.pucp.skillb_ia.model;

import jakarta.persistence.*;

// Miembros de una conversación (chat) — se puebla con el PM + los
// colaboradores con asignación activa al proyecto (A9). El RM nunca entra aquí.
@Entity
@Table(name = "conversacion_usuario")
public class ConversacionUsuario {

    @EmbeddedId
    private ConversacionUsuarioId id = new ConversacionUsuarioId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("conversacion")
    @JoinColumn(name = "conversacion_id", nullable = false)
    private Conversacion conversacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("usuario")
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    public ConversacionUsuarioId getId() { return id; }
    public void setId(ConversacionUsuarioId id) { this.id = id; }

    public Conversacion getConversacion() { return conversacion; }
    public void setConversacion(Conversacion conversacion) { this.conversacion = conversacion; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
}
