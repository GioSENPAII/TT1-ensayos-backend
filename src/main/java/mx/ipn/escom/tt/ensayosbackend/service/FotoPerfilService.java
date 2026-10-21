package mx.ipn.escom.tt.ensayosbackend.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ipn.escom.tt.ensayosbackend.entity.Usuario;
import mx.ipn.escom.tt.ensayosbackend.exception.ApiException;
import mx.ipn.escom.tt.ensayosbackend.repository.InscripcionRepository;
import mx.ipn.escom.tt.ensayosbackend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Foto de perfil de cualquier usuario. La imagen se vuelve a codificar como JPEG de a lo más 512 px por
 * lado: así ocupa poco y se descartan los metadatos del archivo original (EXIF, ubicación GPS).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FotoPerfilService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_LADO_ORIGINAL = 8000; // evita imágenes que agoten la memoria al decodificarse
    private static final int LADO_FINAL = 512;
    private static final Set<String> FORMATOS = Set.of("jpeg", "png");
    private static final String MSG_FORMATO = "La imagen debe ser JPG o PNG";

    private final UsuarioRepository usuarioRepository;
    private final InscripcionRepository inscripcionRepository;
    private final AlmacenamientoService almacenamiento;

    @Transactional
    public void subir(Usuario usuario, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw ApiException.badRequest("Selecciona una imagen");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("La imagen no debe superar 5 MB");
        }
        byte[] jpeg;
        try {
            jpeg = aJpeg(archivo.getBytes());
        } catch (IOException e) {
            throw ApiException.badRequest(MSG_FORMATO);
        }
        String anterior = usuario.getFotoPerfil();
        usuario.setFotoPerfil(almacenamiento.guardarFoto(jpeg, usuario.getIdUsuario()));
        usuarioRepository.save(usuario);
        if (anterior != null) {
            almacenamiento.eliminarAlConfirmar(List.of(anterior));
        }
    }

    @Transactional
    public void eliminar(Usuario usuario) {
        String anterior = usuario.getFotoPerfil();
        if (anterior == null) {
            return;
        }
        usuario.setFotoPerfil(null);
        usuarioRepository.save(usuario);
        almacenamiento.eliminarAlConfirmar(List.of(anterior));
    }

    /**
     * Ven la foto: el propio usuario, el administrador, el profesor de los grupos donde está inscrito el
     * alumno y los alumnos inscritos en algún grupo del profesor.
     */
    @Transactional(readOnly = true)
    public byte[] leer(Usuario solicitante, Long idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> ApiException.notFound("La cuenta no existe"));
        if (!puedeVer(solicitante, usuario)) {
            throw ApiException.forbidden("No tienes acceso a esta foto");
        }
        if (usuario.getFotoPerfil() == null) {
            throw ApiException.notFound("El usuario no tiene foto de perfil");
        }
        return almacenamiento.leer(usuario.getFotoPerfil());
    }

    private boolean puedeVer(Usuario solicitante, Usuario usuario) {
        if (solicitante.getIdUsuario().equals(usuario.getIdUsuario())
                || solicitante.getRol() == Usuario.Rol.ADMINISTRADOR) {
            return true;
        }
        if (solicitante.getRol() == Usuario.Rol.PROFESOR && usuario.getRol() == Usuario.Rol.ALUMNO) {
            return inscripcionRepository.existeProfesorDeAlumno(solicitante, usuario);
        }
        if (solicitante.getRol() == Usuario.Rol.ALUMNO && usuario.getRol() == Usuario.Rol.PROFESOR) {
            return inscripcionRepository.existeProfesorDeAlumno(usuario, solicitante);
        }
        return false;
    }

    private static byte[] aJpeg(byte[] original) throws IOException {
        // Los metadatos se descartan al recodificar, así que el giro indicado en el EXIF se aplica antes
        BufferedImage imagen = orientar(decodificar(original), orientacionExif(original));
        double escala = Math.min(1.0, (double) LADO_FINAL / Math.max(imagen.getWidth(), imagen.getHeight()));
        int ancho = Math.max(1, (int) Math.round(imagen.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(imagen.getHeight() * escala));

        // RGB sobre fondo blanco: JPEG no tiene transparencia
        BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, ancho, alto);
        g.drawImage(imagen, 0, 0, ancho, alto, null);
        g.dispose();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(salida)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(destino, null, null), param);
        } finally {
            writer.dispose();
        }
        return salida.toByteArray();
    }

    /**
     * Valor de la etiqueta Orientation (1 a 8). Los teléfonos guardan la foto como la capta el sensor y
     * anotan aquí cómo girarla; 1 = sin cambios, también si no hay EXIF o no se puede leer.
     */
    static int orientacionExif(byte[] original) {
        try {
            ExifIFD0Directory exif = ImageMetadataReader.readMetadata(new ByteArrayInputStream(original))
                    .getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            log.debug("Sin orientación EXIF legible: {}", e.getMessage());
        }
        return 1;
    }

    /** Aplica la transformación de la orientación EXIF (giros de 90/180/270 y reflejos). */
    static BufferedImage orientar(BufferedImage imagen, int orientacion) {
        if (orientacion < 2 || orientacion > 8) {
            return imagen;
        }
        int w = imagen.getWidth();
        int h = imagen.getHeight();
        boolean intercambia = orientacion >= 5; // 5 a 8 giran 90°: ancho y alto se intercambian
        AffineTransform t = new AffineTransform();
        switch (orientacion) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(-Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(-Math.PI / 2); }
            default -> { }
        }
        BufferedImage destino = new BufferedImage(intercambia ? h : w, intercambia ? w : h,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = destino.createGraphics();
        g.drawImage(imagen, t, null);
        g.dispose();
        return destino;
    }

    /** Solo JPEG o PNG, reconocidos por su contenido y no por la extensión. */
    private static BufferedImage decodificar(byte[] original) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException(MSG_FORMATO);
            }
            ImageReader reader = readers.next();
            try {
                if (!FORMATOS.contains(reader.getFormatName().toLowerCase())) {
                    throw new IOException(MSG_FORMATO);
                }
                reader.setInput(in, true, true);
                if (reader.getWidth(0) > MAX_LADO_ORIGINAL || reader.getHeight(0) > MAX_LADO_ORIGINAL) {
                    throw ApiException.badRequest("La imagen es demasiado grande (máximo 8000 px por lado)");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }
}
