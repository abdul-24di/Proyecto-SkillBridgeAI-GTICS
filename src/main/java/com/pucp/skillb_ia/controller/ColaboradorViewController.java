package com.pucp.skillb_ia.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/colaborador")
public class ColaboradorViewController {

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/colaborador/dashboard";
    }

    @GetMapping({"/dashboard", "/col-dashboard.html"})
    public String dashboard() {
        return "col/col-dashboard";
    }

    @GetMapping({"/proyectos", "/col-proyectos.html"})
    public String projects() {
        return "col/col-proyectos";
    }

    @GetMapping({"/proyectos/detalle", "/col-detalle-proyecto.html"})
    public String projectDetail() {
        return "col/col-detalle-proyecto";
    }

    @GetMapping({"/foros", "/col-foros.html"})
    public String forums() {
        return "col/col-foros";
    }

    @GetMapping({"/asistente", "/col-asistente.html"})
    public String assistant() {
        return "col/col-asistente";
    }

    @GetMapping({"/perfil", "/col-perfil.html"})
    public String profile() {
        return "col/col-perfil";
    }

    @GetMapping({"/explorar", "/col-explorar.html"})
    public String explore() {
        return "col/col-explorar";
    }


}