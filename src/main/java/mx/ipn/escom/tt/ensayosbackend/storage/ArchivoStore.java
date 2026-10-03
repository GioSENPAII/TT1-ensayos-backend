package mx.ipn.escom.tt.ensayosbackend.storage;

/** Dónde viven los PDF. Las rutas son relativas (p. ej. "tarea-6/alumno-3-uuid.pdf"). */
public interface ArchivoStore {

    void escribir(String ruta, byte[] contenido);

    /** @throws ArchivoNoEncontradoException si el archivo no existe */
    byte[] leer(String ruta);

    /** No falla si el archivo ya no existe. */
    void borrar(String ruta);

    class ArchivoNoEncontradoException extends RuntimeException {
        public ArchivoNoEncontradoException(String ruta) {
            super("No existe el archivo " + ruta);
        }
    }
}
