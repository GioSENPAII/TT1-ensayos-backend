package mx.ipn.escom.tt.ensayosbackend.service;

import mx.ipn.escom.tt.ensayosbackend.exception.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Service
public class PdfService {

    private static final byte[] FIRMA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    /** La extensión puede mentir: se revisa la firma "%PDF-" del contenido. */
    public boolean esPdf(byte[] contenido) {
        return contenido.length >= FIRMA_PDF.length
                && Arrays.equals(Arrays.copyOf(contenido, FIRMA_PDF.length), FIRMA_PDF);
    }

    /**
     * Extrae el texto plano. RN-IA-01: si el PDF no tiene texto (escaneado) se rechaza sin intentar OCR.
     */
    public String extraerTexto(byte[] contenido) {
        try (PDDocument doc = Loader.loadPDF(contenido)) {
            String texto = new PDFTextStripper().getText(doc).strip();
            if (texto.isEmpty()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "El PDF no contiene texto seleccionable. Exporta tu ensayo como PDF digital, no escaneado.");
            }
            return texto;
        } catch (InvalidPasswordException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "El PDF está protegido con contraseña.");
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "El archivo PDF está dañado o no se puede leer.");
        }
    }
}
