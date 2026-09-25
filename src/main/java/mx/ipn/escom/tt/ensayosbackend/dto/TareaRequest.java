package mx.ipn.escom.tt.ensayosbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** Creación (requiere groupId) y edición (groupId se ignora) de tareas, CU-WEB-05. */
@Data
public class TareaRequest {
    private Long groupId;
    @NotBlank(message = "El nombre de la tarea es obligatorio")
    @Size(max = 150, message = "El nombre de la tarea es demasiado largo")
    private String nombre;
    @NotNull(message = "La fecha de apertura es obligatoria")
    private LocalDateTime fechaApertura;
    @NotNull(message = "La fecha de cierre es obligatoria")
    private LocalDateTime fechaCierre;
}
