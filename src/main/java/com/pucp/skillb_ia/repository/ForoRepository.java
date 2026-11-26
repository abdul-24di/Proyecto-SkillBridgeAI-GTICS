package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ForoRepository extends JpaRepository<Foro, Long> {
    Optional<Foro> findByProyecto(Proyecto proyecto);

    // A7/A9: el RM lee todos los foros de la organización sin necesidad de asignación.
    List<Foro> findByEsPublicoTrue();

    // Los que ven todos los usuarios (Foro.isVisibleParaTodos): comunidad, anuncios y proyectos públicos.
    @Query("select f from Foro f where f.esPublico = true or f.tipo = com.pucp.skillb_ia.model.enums.TipoForo.GENERAL")
    List<Foro> findVisiblesParaTodos();

    @Query("""
            select f from Foro f
            left join fetch f.proyecto p
            left join fetch p.pm
            order by f.fechaCreacion desc
            """)
    List<Foro> findTodosConProyectoYPm();

    @Query("""
            select f from Foro f
            left join fetch f.proyecto p
            left join fetch p.pm
            where f.id = :id
            """)
    Optional<Foro> findByIdConProyectoYPm(@Param("id") Long id);

    // Eliminar un foro de comunidad con todo su contenido. La base no borra en cascada, así que antes de borrar
    // el foro se borran, en este orden, los votos de las respuestas, las respuestas, los votos de las
    // publicaciones y las publicaciones.
    @Modifying
    @Query("delete from VotoRespuesta v where v.respuesta.id in "
            + "(select r.id from RespuestaForo r where r.publicacion.foro.id = :foroId)")
    void borrarVotosDeRespuestas(@Param("foroId") Long foroId);

    @Modifying
    @Query("delete from RespuestaForo r where r.publicacion.id in "
            + "(select p.id from PublicacionForo p where p.foro.id = :foroId)")
    void borrarRespuestas(@Param("foroId") Long foroId);

    @Modifying
    @Query("delete from VotoPublicacion v where v.publicacion.id in "
            + "(select p.id from PublicacionForo p where p.foro.id = :foroId)")
    void borrarVotosDePublicaciones(@Param("foroId") Long foroId);

    @Modifying
    @Query("delete from PublicacionForo p where p.foro.id = :foroId")
    void borrarPublicaciones(@Param("foroId") Long foroId);
}
