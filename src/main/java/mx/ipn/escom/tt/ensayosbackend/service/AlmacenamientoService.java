package mx.ipn.escom.tt.ensayosbackend.service;

import lombok.extern.slf4j.Slf4j;
import mx.ipn.escom.tt.ensayosbackend.storage.ArchivoStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * PDFs de las entregas y del histórico de similitud. Dónde se guardan lo decide app.storage.tipo:
 * "disco" en desarrollo o "gcs" (bucket de Cloud Storage) en la nube, como prevé la sección 4.2.
 */
@Slf4j
@Service
public class AlmacenamientoService {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final ArchivoStore store;
    private final ArchivoStore similitud;

    public AlmacenamientoService(@Qualifier("entregasStore") ArchivoStore store,
                                 @Qualifier("similitudStore") ArchivoStore similitud) {
        this.store = store;
        this.similitud = similitud;
    }

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
     * Ensayo del histórico del motor de IA, guardado como "{hash}.pdf".
     *
     * @throws ArchivoStore.ArchivoNoEncontradoException si el histórico no tiene ese ensayo
     */
    public byte[] leerSimilar(String hash) {
        String h = hash == null ? "" : hash.toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(h).matches()) {
            throw new ArchivoStore.ArchivoNoEncontradoException(hash + ".pdf");
        }
        return similitud.leer(h + ".pdf");
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
