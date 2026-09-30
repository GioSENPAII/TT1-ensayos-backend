package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ipn.escom.tt.ensayosbackend.storage.ArchivoStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * PDFs de las entregas. Dónde se guardan lo decide app.storage.tipo: "disco" en desarrollo o
 * "gcs" (bucket de Cloud Storage) en la nube, como prevé la sección 4.2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlmacenamientoService {

    private final ArchivoStore store;

    /** Devuelve la ruta relativa guardada en ensayos.ruta_archivo. */
    public String guardar(byte[] contenido, Long idTarea, Long idAlumno) {
        String relativa = "tarea-" + idTarea + "/alumno-" + idAlumno + "-" + UUID.randomUUID() + ".pdf";
        store.escribir(relativa, contenido);
        return relativa;
    }

    public byte[] leer(String relativa) {
        return store.leer(relativa);
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
        store.borrar(relativa);
    }
}
