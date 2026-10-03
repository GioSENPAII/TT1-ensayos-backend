package mx.ipn.escom.tt.ensayosbackend.dto;

/** PDF que se devuelve como descarga. */
public record ArchivoDescarga(String nombre, byte[] contenido) {
}
