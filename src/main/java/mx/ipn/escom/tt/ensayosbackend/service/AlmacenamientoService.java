package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Guarda los PDF en disco local (app.storage.dir). En producción la sección 4.2 prevé un bucket;
 * al cambiar solo se reemplaza esta clase.
 */
@Slf4j
@Service
public class AlmacenamientoService {

    private final Path raiz;

    public AlmacenamientoService(@Value("${app.storage.dir}") String dir) {
        this.raiz = Path.of(dir).toAbsolutePath().normalize();
    }

    /** Devuelve la ruta relativa guardada en ensayos.ruta_archivo. */
    public String guardar(byte[] contenido, Long idTarea, Long idAlumno) {
        String relativa = "tarea-" + idTarea + "/alumno-" + idAlumno + "-" + UUID.randomUUID() + ".pdf";
        Path destino = raiz.resolve(relativa);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
            return relativa;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el PDF", e);
        }
    }

    public byte[] leer(String relativa) {
        try {
            return Files.readAllBytes(raiz.resolve(relativa));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el PDF", e);
        }
    }

    public void eliminar(String relativa) {
        try {
            Files.deleteIfExists(raiz.resolve(relativa));
        } catch (IOException e) {
            log.warn("No se pudo borrar {}: {}", relativa, e.getMessage());
        }
    }
}
