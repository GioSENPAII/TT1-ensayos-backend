package mx.ipn.escom.tt.ensayosbackend.storage;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Dos ubicaciones de PDFs, ambas en disco (desarrollo) o en Cloud Storage (nube) según app.storage.tipo:
 * las entregas de los alumnos y el histórico de similitud del motor de IA (archivos "{sha256}.pdf").
 */
@Configuration
public class AlmacenamientoConfig {

    private final boolean gcs;
    private Storage storage; // un solo cliente para los dos buckets

    public AlmacenamientoConfig(@Value("${app.storage.tipo}") String tipo) {
        if (!tipo.equals("disco") && !tipo.equals("gcs")) {
            throw new IllegalStateException("app.storage.tipo debe ser \"disco\" o \"gcs\", no \"" + tipo + "\"");
        }
        this.gcs = tipo.equals("gcs");
    }

    @Bean
    @Primary
    public ArchivoStore entregasStore(@Value("${app.storage.dir}") String dir,
                                      @Value("${app.storage.bucket}") String bucket) {
        return crear(dir, bucket, "STORAGE_BUCKET");
    }

    @Bean
    public ArchivoStore similitudStore(@Value("${app.storage.similitud.dir}") String dir,
                                       @Value("${app.storage.similitud.bucket}") String bucket) {
        return crear(dir, bucket, "STORAGE_SIMILITUD_BUCKET");
    }

    private ArchivoStore crear(String dir, String bucket, String variable) {
        if (!gcs) {
            return new DiscoArchivoStore(dir);
        }
        if (bucket.isBlank()) {
            throw new IllegalStateException("app.storage.tipo=gcs requiere " + variable);
        }
        return new GcsArchivoStore(storage(), bucket);
    }

    private Storage storage() {
        if (storage == null) {
            storage = StorageOptions.getDefaultInstance().getService();
        }
        return storage;
    }
}
