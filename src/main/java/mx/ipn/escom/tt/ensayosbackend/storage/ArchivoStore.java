package mx.ipn.escom.tt.ensayosbackend.storage;

/** Dónde viven los PDF de las entregas. Las rutas son relativas (p. ej. "tarea-6/alumno-3-uuid.pdf"). */
public interface ArchivoStore {

    void escribir(String ruta, byte[] contenido);

    byte[] leer(String ruta);

    /** No falla si el archivo ya no existe. */
    void borrar(String ruta);
}
