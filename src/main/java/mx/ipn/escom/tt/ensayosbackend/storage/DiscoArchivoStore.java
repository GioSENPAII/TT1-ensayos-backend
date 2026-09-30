package mx.ipn.escom.tt.ensayosbackend.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Desarrollo local: disco (app.storage.dir). */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.tipo", havingValue = "disco", matchIfMissing = true)
public class DiscoArchivoStore implements ArchivoStore {

    private final Path raiz;

    public DiscoArchivoStore(@Value("${app.storage.dir}") String dir) {
        this.raiz = Path.of(dir).toAbsolutePath().normalize();
        log.info("PDFs en disco: {}", raiz);
    }

    @Override
    public void escribir(String ruta, byte[] contenido) {
        Path destino = resolver(ruta);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el PDF", e);
        }
    }

    @Override
    public byte[] leer(String ruta) {
        try {
            return Files.readAllBytes(resolver(ruta));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el PDF", e);
        }
    }

    @Override
    public void borrar(String ruta) {
        try {
            Files.deleteIfExists(resolver(ruta));
        } catch (IOException e) {
            log.warn("No se pudo borrar {}: {}", ruta, e.getMessage());
        }
    }

    // Evita que una ruta con "../" salga de la carpeta de almacenamiento
    private Path resolver(String ruta) {
        Path p = raiz.resolve(ruta).normalize();
        if (!p.startsWith(raiz)) {
            throw new IllegalArgumentException("Ruta fuera del almacenamiento: " + ruta);
        }
        return p;
    }
}
