package mx.ipn.escom.tt.ensayosbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.dto.CuentaResponse;
import mx.ipn.escom.tt.ensayosbackend.dto.EstadoCuentaRequest;
import mx.ipn.escom.tt.ensayosbackend.dto.PaginaResponse;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import mx.ipn.escom.tt.ensayosbackend.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Gestión de cuentas (Tabla 55, CU-WEB-03). SecurityConfig ya restringe /users/** al administrador. */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@RequiredArgsConstructor
public class UserController {

    private final AdminService adminService;

    @GetMapping
    public PaginaResponse<CuentaResponse> directorio(@RequestParam(required = false) Usuario.Rol rol,
                                                     @RequestParam(required = false) Usuario.Estado estado,
                                                     @RequestParam(required = false) String q,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return adminService.directorio(rol, estado, q, page, size);
    }

    @GetMapping("/{id}")
    public CuentaResponse detalle(@PathVariable Long id) {
        return adminService.detalle(id);
    }

    @PatchMapping("/{id}/status")
    public CuentaResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoCuentaRequest request) {
        return adminService.cambiarEstado(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        adminService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
