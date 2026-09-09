package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.TipoVoto;
import jakarta.persistence.*;

// A8: mismo esquema de votación que VotoPublicacion, pero sobre respuestas.
@Entity
@Table(name = "voto_respuesta")
public class VotoRespuesta {

    @EmbeddedId
    private VotoRespuestaId id = new VotoRespuestaId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("usuario")
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("respuesta")
    @JoinColumn(name = "respuesta_id", nullable = false)
    private RespuestaForo respuesta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoVoto tipo;

    public VotoRespuestaId getId() { return id; }
    public void setId(VotoRespuestaId id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public RespuestaForo getRespuesta() { return respuesta; }
    public void setRespuesta(RespuestaForo respuesta) { this.respuesta = respuesta; }

    public TipoVoto getTipo() { return tipo; }
    public void setTipo(TipoVoto tipo) { this.tipo = tipo; }
}
