package mx.ipn.escom.tt.ensayosbackend.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Nube: bucket de Cloud Storage (sección 4.2). Usa la cuenta de servicio de Cloud Run
 * (Application Default Credentials), sin llaves en el código.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.tipo", havingValue = "gcs")
public class GcsArchivoStore implements ArchivoStore {

    private final Storage storage;
    private final String bucket;

    public GcsArchivoStore(@Value("${app.storage.bucket}") String bucket) {
        if (bucket.isBlank()) {
            throw new IllegalStateException("app.storage.tipo=gcs requiere STORAGE_BUCKET");
        }
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bucket = bucket;
        log.info("PDFs en Cloud Storage: gs://{}", bucket);
    }

    @Override
    public void escribir(String ruta, byte[] contenido) {
        BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket, ruta)).setContentType("application/pdf").build();
        storage.create(info, contenido);
    }

    @Override
    public byte[] leer(String ruta) {
        byte[] contenido = storage.readAllBytes(BlobId.of(bucket, ruta));
        if (contenido == null) {
            throw new IllegalStateException("No existe gs://" + bucket + "/" + ruta);
        }
        return contenido;
    }

    @Override
    public void borrar(String ruta) {
        try {
            storage.delete(BlobId.of(bucket, ruta));
        } catch (StorageException e) {
            log.warn("No se pudo borrar gs://{}/{}: {}", bucket, ruta, e.getMessage());
        }
    }
}
