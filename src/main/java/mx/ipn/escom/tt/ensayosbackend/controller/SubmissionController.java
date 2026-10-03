package mx.ipn.escom.tt.ensayosbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.dto.AjusteCalificacionRequest;
import mx.ipn.escom.tt.ensayosbackend.dto.ArchivoDescarga;
import mx.ipn.escom.tt.ensayosbackend.dto.EntregaResponse;
import mx.ipn.escom.tt.ensayosbackend.dto.ReporteResponse;
import mx.ipn.escom.tt.ensayosbackend.service.EntregaService;
import mx.ipn.escom.tt.ensayosbackend.service.UsuarioActualService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Entregas y calificaciones (Tabla 55). */
@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final EntregaService entregaService;
    private final UsuarioActualService usuarioActual;

    // CU-ALU-02 (asíncrono, C6): multipart con "file" (PDF ≤ 10 MB) y "assignmentId"
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ALUMNO')")
    public ResponseEntity<EntregaResponse> enviar(@RequestParam("assignmentId") Long assignmentId,
                                                  @RequestPart("file") MultipartFile file) {
        EntregaResponse entrega = entregaService.enviar(usuarioActual.obtener(), assignmentId, file);
        // 202: la entrega quedó EN_REVISION; la calificación llega después (consultar Location)
        return ResponseEntity.accepted().location(ServletUriComponentsBuilder.fromCurrentRequest()
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

    // PDF que subió el alumno (cualquier estado)
    @GetMapping("/{id}/file")
    @PreAuthorize("hasRole('PROFESOR')")
    public ResponseEntity<byte[]> archivo(@PathVariable Long id) {
        return pdf(entregaService.archivo(usuarioActual.obtener(), id));
    }

    // Solo con posible plagio: el ensayo del histórico con mayor similitud
    @GetMapping("/{id}/similar-file")
    @PreAuthorize("hasRole('PROFESOR')")
    public ResponseEntity<byte[]> archivoSimilar(@PathVariable Long id) {
        return pdf(entregaService.archivoSimilar(usuarioActual.obtener(), id));
    }

    // CU-WEB-02: ajuste manual de un criterio
    @PatchMapping("/{id}/grading")
    @PreAuthorize("hasRole('PROFESOR')")
    public ReporteResponse ajustar(@PathVariable Long id, @Valid @RequestBody AjusteCalificacionRequest request) {
        return entregaService.ajustar(usuarioActual.obtener(), id, request);
    }

    private static ResponseEntity<byte[]> pdf(ArchivoDescarga archivo) {
        // filename* (RFC 5987) solo si el nombre trae acentos u otros caracteres fuera de ASCII
        String nombre = archivo.nombre();
        ContentDisposition.Builder disposicion = ContentDisposition.attachment();
        if (StandardCharsets.US_ASCII.newEncoder().canEncode(nombre)) {
            disposicion.filename(nombre);
        } else {
            disposicion.filename(nombre, StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.build().toString())
                .body(archivo.contenido());
    }
}
