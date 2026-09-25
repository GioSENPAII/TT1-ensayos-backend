package mx.ipn.escom.tt.ensayosbackend.controller;

import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.dto.GrupoAlumnoResponse;
import mx.ipn.escom.tt.ensayosbackend.dto.EntregaResponse;
import mx.ipn.escom.tt.ensayosbackend.service.EntregaService;
import mx.ipn.escom.tt.ensayosbackend.service.GrupoService;
import mx.ipn.escom.tt.ensayosbackend.service.UsuarioActualService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Recursos del alumno autenticado. SecurityConfig restringe /students/me/** al rol ALUMNO. */
@RestController
@RequestMapping("/api/v1/students/me")
@RequiredArgsConstructor
public class StudentController {

    private final GrupoService grupoService;
    private final EntregaService entregaService;
    private final UsuarioActualService usuarioActual;

    @GetMapping("/groups")
    public List<GrupoAlumnoResponse> misGrupos() {
        return grupoService.misGrupos(usuarioActual.obtener());
    }

    // CU-ALU-04: historial de entregas
    @GetMapping("/submissions")
    public List<EntregaResponse> misEntregas() {
        return entregaService.historial(usuarioActual.obtener());
    }
}
