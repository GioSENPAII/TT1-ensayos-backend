package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    /**
     * Borra los PDF cuando la transacción actual se confirme. Si la eliminación en la BD falla y se
     * revierte (CU-WEB-03 E2), los archivos se conservan.
     */
    public void eliminarAlConfirmar(List<String> rutas) {
        if (rutas.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            rutas.forEach(this::eliminar);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rutas.forEach(AlmacenamientoService.this::eliminar);
                log.info("{} PDF(s) eliminados del almacenamiento", rutas.size());
            }
        });
    }

    public void eliminar(String relativa) {
        try {
            Files.deleteIfExists(raiz.resolve(relativa));
        } catch (IOException e) {
            log.warn("No se pudo borrar {}: {}", relativa, e.getMessage());
        }
    }
}
