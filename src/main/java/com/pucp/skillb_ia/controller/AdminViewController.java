package com.pucp.skillb_ia.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminViewController {

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping({"/dashboard", "/admin-dashboard.html"})
    public String dashboard() {
        return "admin/admin-dashboard";
    }

    @GetMapping({"/usuarios", "/admin-usuarios.html"})
    public String users() {
        return "admin/admin-usuarios";
    }

    @GetMapping({"/habilidades", "/admin-habilidades.html"})
    public String skills() {
        return "admin/admin-habilidades";
    }

    @GetMapping({"/configuracion", "/admin-configuracion.html"})
    public String configuration() {
        return "admin/admin-configuracion";
    }

    @GetMapping({"/auditoria", "/admin-auditoria.html"})
    public String audit() {
        return "admin/admin-auditoria";
    }
}
