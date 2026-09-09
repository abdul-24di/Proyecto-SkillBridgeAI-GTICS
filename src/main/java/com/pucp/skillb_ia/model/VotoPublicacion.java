package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.TipoVoto;
import jakarta.persistence.*;

// A7: un voto por usuario por publicación, modificable/retirable (por eso la
// PK es compuesta usuario+publicación, no un id propio).
@Entity
@Table(name = "voto_publicacion")
public class VotoPublicacion {

    @EmbeddedId
    private VotoPublicacionId id = new VotoPublicacionId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("usuario")
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("publicacion")
    @JoinColumn(name = "publicacion_id", nullable = false)
    private PublicacionForo publicacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoVoto tipo;

    public VotoPublicacionId getId() { return id; }
    public void setId(VotoPublicacionId id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public PublicacionForo getPublicacion() { return publicacion; }
    public void setPublicacion(PublicacionForo publicacion) { this.publicacion = publicacion; }

    public TipoVoto getTipo() { return tipo; }
    public void setTipo(TipoVoto tipo) { this.tipo = tipo; }
}
