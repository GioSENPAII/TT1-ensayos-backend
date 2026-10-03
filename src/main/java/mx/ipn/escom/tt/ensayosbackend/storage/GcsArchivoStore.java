package mx.ipn.escom.tt.ensayosbackend.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import lombok.extern.slf4j.Slf4j;

/**
 * Nube: bucket de Cloud Storage (sección 4.2). Usa la cuenta de servicio de Cloud Run
 * (Application Default Credentials), sin llaves en el código.
 */
@Slf4j
public class GcsArchivoStore implements ArchivoStore {

    private final Storage storage;
    private final String bucket;

    public GcsArchivoStore(Storage storage, String bucket) {
        this.storage = storage;
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
        try {
            return storage.readAllBytes(BlobId.of(bucket, ruta));
        } catch (StorageException e) {
            if (e.getCode() == 404) {
                throw new ArchivoNoEncontradoException("gs://" + bucket + "/" + ruta);
            }
            throw e;
        }
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
