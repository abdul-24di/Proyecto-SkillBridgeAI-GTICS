package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class DisponibilidadService {

    private final AsignacionRepository asignacionRepository;
    private final UsuarioRepository usuarioRepository;

    public DisponibilidadService(AsignacionRepository asignacionRepository, UsuarioRepository usuarioRepository) {
        this.asignacionRepository = asignacionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    //Recalculamos y guardamos cuántas horas/semana le quedan libres a un colaborador,
    //Utilizamos este metodo cada vez que una asignación empieza o deja de estar ACTIVA,
    //para que el número nunca quede desactualizado.
    @Transactional
    public void recalcular(Usuario colaborador) {
        BigDecimal contratadas = colaborador.getHorasContratadasSemana();
        if (contratadas == null) {
            colaborador.setHorasDisponibles(BigDecimal.ZERO);
            usuarioRepository.save(colaborador);
            return;
        }

        BigDecimal comprometidas = asignacionRepository.sumHorasSemanalesActivasPorColaborador(colaborador);
        BigDecimal disponibles = contratadas.subtract(comprometidas);

        //En caso de que las horas disponibles sean menores a cero, las establecemos en cero.
        //Empleamos signum() porque devuelve -1 cuando el valor es negativo.
        if (disponibles.signum() < 0) {
            disponibles = BigDecimal.ZERO;
        }

        colaborador.setHorasDisponibles(disponibles);
        usuarioRepository.save(colaborador);
    }
}