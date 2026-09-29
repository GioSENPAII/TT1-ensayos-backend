package mx.ipn.escom.tt.ensayosbackend.controller;

import lombok.RequiredArgsConstructor;
import mx.ipn.escom.tt.ensayosbackend.dto.MetricasResponse;
import mx.ipn.escom.tt.ensayosbackend.service.AdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Panel del administrador (sección 4.5.5). */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/metrics")
    public MetricasResponse metricas() {
        return adminService.metricas();
    }
}
