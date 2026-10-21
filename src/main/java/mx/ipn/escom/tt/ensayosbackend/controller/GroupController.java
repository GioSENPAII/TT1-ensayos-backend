package mx.ipn.escom.tt.ensayosbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.dto.*;
import mx.ipn.escom.tt.ensayosbackend.service.GrupoService;
import mx.ipn.escom.tt.ensayosbackend.service.RendimientoService;
import mx.ipn.escom.tt.ensayosbackend.service.UsuarioActualService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Grupos académicos (CU-WEB-01, CU-WEB-04, CU-ALU-01). */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GrupoService grupoService;
    private final UsuarioActualService usuarioActual;
    private final RendimientoService rendimientoService;

    @PostMapping
    @PreAuthorize("hasRole('PROFESOR')")
    public ResponseEntity<GrupoResponse> crear(@Valid @RequestBody CrearGrupoRequest request) {
        GrupoResponse grupo = grupoService.crear(usuarioActual.obtener(), request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(grupo.getId()).toUri()).body(grupo);
    }

    @GetMapping
    @PreAuthorize("hasRole('PROFESOR')")
    public List<GrupoResponse> listar() {
        return grupoService.listarDelProfesor(usuarioActual.obtener());
    }

    // Gráficas de rendimiento: promedio de cada grupo del profesor
    @GetMapping("/performance")
    @PreAuthorize("hasRole('PROFESOR')")
    public List<ResumenRendimientoResponse> rendimiento() {
        return rendimientoService.resumen(usuarioActual.obtener());
    }

    // Gráficas de rendimiento de un grupo: por tarea, por alumno, por criterio y distribución
    @GetMapping("/{id}/performance")
    @PreAuthorize("hasRole('PROFESOR')")
    public RendimientoGrupoResponse rendimientoGrupo(@PathVariable Long id) {
        return rendimientoService.detalle(usuarioActual.obtener(), id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR')")
    public GrupoResponse obtener(@PathVariable Long id) {
        return grupoService.obtener(usuarioActual.obtener(), id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        grupoService.eliminar(usuarioActual.obtener(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('PROFESOR')")
    public GrupoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoGrupoRequest request) {
        return grupoService.cambiarEstado(usuarioActual.obtener(), id, request);
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasRole('PROFESOR')")
    public List<AlumnoInscritoResponse> alumnos(@PathVariable Long id) {
        return grupoService.alumnos(usuarioActual.obtener(), id);
    }

    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasRole('PROFESOR')")
    public ResponseEntity<Void> removerAlumno(@PathVariable Long id, @PathVariable Long studentId) {
        grupoService.removerAlumno(usuarioActual.obtener(), id, studentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole('ALUMNO')")
    public ResponseEntity<GrupoAlumnoResponse> unirse(@Valid @RequestBody UnirseGrupoRequest request) {
        GrupoAlumnoResponse grupo = grupoService.unirse(usuarioActual.obtener(), request);
        return ResponseEntity.status(201).body(grupo);
    }
}
