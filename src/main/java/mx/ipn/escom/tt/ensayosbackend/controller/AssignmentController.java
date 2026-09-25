package mx.ipn.escom.tt.ensayosbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.dto.TareaRequest;
import mx.ipn.escom.tt.ensayosbackend.dto.TareaResponse;
import mx.ipn.escom.tt.ensayosbackend.service.TareaService;
import mx.ipn.escom.tt.ensayosbackend.service.UsuarioActualService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Tareas de entrega (CU-WEB-05). */
@RestController
@RequestMapping("/api/v1/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final TareaService tareaService;
    private final UsuarioActualService usuarioActual;

    @PostMapping
    @PreAuthorize("hasRole('PROFESOR')")
    public ResponseEntity<TareaResponse> crear(@Valid @RequestBody TareaRequest request) {
        TareaResponse tarea = tareaService.crear(usuarioActual.obtener(), request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/assignments/{id}").buildAndExpand(tarea.getId()).toUri()).body(tarea);
    }

    // Profesor: todas las tareas de su grupo. Alumno inscrito: solo las ya abiertas, con su entrega.
    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESOR','ALUMNO')")
    public List<TareaResponse> listar(@RequestParam Long groupId) {
        return tareaService.listar(usuarioActual.obtener(), groupId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR')")
    public TareaResponse actualizar(@PathVariable Long id, @Valid @RequestBody TareaRequest request) {
        return tareaService.actualizar(usuarioActual.obtener(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        tareaService.eliminar(usuarioActual.obtener(), id);
        return ResponseEntity.noContent().build();
    }
}
