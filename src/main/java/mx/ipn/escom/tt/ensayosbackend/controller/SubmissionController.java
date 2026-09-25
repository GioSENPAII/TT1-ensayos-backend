package mx.ipn.escom.tt.ensayosbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.dto.AjusteCalificacionRequest;
import mx.ipn.escom.tt.ensayosbackend.dto.EntregaResponse;
import mx.ipn.escom.tt.ensayosbackend.dto.ReporteResponse;
import mx.ipn.escom.tt.ensayosbackend.service.EntregaService;
import mx.ipn.escom.tt.ensayosbackend.service.UsuarioActualService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Entregas y calificaciones (Tabla 55). */
@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final EntregaService entregaService;
    private final UsuarioActualService usuarioActual;

    // CU-ALU-02: multipart con "file" (PDF ≤ 10 MB) y "assignmentId"
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ALUMNO')")
    public ResponseEntity<EntregaResponse> enviar(@RequestParam("assignmentId") Long assignmentId,
                                                  @RequestPart("file") MultipartFile file) {
        EntregaResponse entrega = entregaService.enviar(usuarioActual.obtener(), assignmentId, file);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(entrega.getId()).toUri()).body(entrega);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ALUMNO','PROFESOR')")
    public EntregaResponse detalle(@PathVariable Long id) {
        return entregaService.detalle(usuarioActual.obtener(), id);
    }

    @GetMapping
    @PreAuthorize("hasRole('PROFESOR')")
    public List<EntregaResponse> porTarea(@RequestParam Long assignmentId) {
        return entregaService.entregasDeTarea(usuarioActual.obtener(), assignmentId);
    }

    @GetMapping("/{id}/grading")
    @PreAuthorize("hasAnyRole('ALUMNO','PROFESOR')")
    public ReporteResponse reporte(@PathVariable Long id) {
        return entregaService.reporte(usuarioActual.obtener(), id);
    }

    // CU-WEB-02: ajuste manual de un criterio
    @PatchMapping("/{id}/grading")
    @PreAuthorize("hasRole('PROFESOR')")
    public ReporteResponse ajustar(@PathVariable Long id, @Valid @RequestBody AjusteCalificacionRequest request) {
        return entregaService.ajustar(usuarioActual.obtener(), id, request);
    }
}
