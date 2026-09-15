package com.pucp.skillb_ia.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/pm")
public class PmViewController {

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/pm/proyectos";
    }

    

    @GetMapping({"/proyectos", "/pm-proyectos.html"})
    public String projects() {
        return "pm/pm-proyectos";
    }

    @GetMapping({"/proyectos/crear", "/pm-crear-proyecto.html"})
    public String createProject() {
        return "pm/pm-crear-proyecto";
    }

    @GetMapping({"/proyectos/detalle", "/pm-detalle-proyecto.html"})
    public String projectDetail() {
        return "pm/pm-detalle-proyecto";
    }

    @GetMapping({"/proyectos/asignaciones", "/pm-asignaciones-proyecto.html"})
    public String projectAssignments() {
        return "pm/pm-asignaciones-proyecto";
    }

    @GetMapping({"/foros", "/pm-foros.html"})
    public String forums() {
        return "pm/pm-foros";
    }

    @GetMapping({"/foro/detalle", "/pm-foro-detalle.html"})
    public String forumDetail() {
        return "pm/pm-foro-detalle";
    }

    @GetMapping({"/chat", "/pm-chat.html"})
    public String chat() {
        return "pm/pm-chat";
    }

    @GetMapping({"/reportes", "/pm-reportes.html"})
    public String reports() {
        return "pm/pm-reportes";
    }

    @GetMapping({"/reportes/detalle", "/pm-reporte-detalle.html"})
    public String reportDetail() {
        return "pm/pm-reporte-detalle";
    }

    @GetMapping({"/asistente", "/pm-asistente-ia.html"})
    public String assistant() {
        return "pm/pm-asistente-ia";
    }

    @GetMapping("/perfil")
    public String perfil() {
        return "pm/pm-perfil";
    }
}