package com.pucp.skillb_ia;

import com.pucp.skillb_ia.controller.AdminViewController;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.security.UsuarioDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AdminCursosRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testRender() throws Exception {
        Usuario adminUser = new Usuario();
        adminUser.setNombre("Test");
        adminUser.setApellido("Admin");
        adminUser.setEmail("test@admin.com");
        UsuarioDetails principal = new UsuarioDetails(adminUser);

        try {
            MvcResult result = mockMvc.perform(get("/admin/cursos").with(user(principal)))
                    .andReturn();
            
            Exception ex = result.getResolvedException();
            if (ex != null) {
                ex.printStackTrace();
            } else {
                System.out.println("NO EXCEPTION! Response status: " + result.getResponse().getStatus());
                System.out.println("HTML length: " + result.getResponse().getContentAsString().length());
                String html = result.getResponse().getContentAsString();
                System.out.println(html.substring(Math.max(0, html.length() - 500)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
