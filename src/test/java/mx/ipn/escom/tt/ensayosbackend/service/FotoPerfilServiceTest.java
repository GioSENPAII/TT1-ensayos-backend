package mx.ipn.escom.tt.ensayosbackend.service;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FotoPerfilServiceTest {

    private static final int W = 3;
    private static final int H = 2;

    /** Imagen de 3×2 con un color distinto en cada píxel. */
    private static BufferedImage original() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img.setRGB(x, y, 0xFF000000 | (x * 40 + 10) << 16 | (y * 90 + 10) << 8);
            }
        }
        return img;
    }

    /** Compara con la definición de la etiqueta Orientation: de qué píxel original viene cada píxel. */
    private static void verificar(int orientacion, boolean gira, BiFunction<Integer, Integer, int[]> origen) {
        BufferedImage s = original();
        BufferedImage d = FotoPerfilService.orientar(s, orientacion);
        assertEquals(gira ? H : W, d.getWidth(), "ancho, orientación " + orientacion);
        assertEquals(gira ? W : H, d.getHeight(), "alto, orientación " + orientacion);
        for (int y = 0; y < d.getHeight(); y++) {
            for (int x = 0; x < d.getWidth(); x++) {
                int[] o = origen.apply(x, y);
                assertEquals(s.getRGB(o[0], o[1]), d.getRGB(x, y),
                        "orientación " + orientacion + " en (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void aplicaLasOchoOrientaciones() {
        verificar(1, false, (x, y) -> new int[]{x, y});
        verificar(2, false, (x, y) -> new int[]{W - 1 - x, y});
        verificar(3, false, (x, y) -> new int[]{W - 1 - x, H - 1 - y});
        verificar(4, false, (x, y) -> new int[]{x, H - 1 - y});
        verificar(5, true, (x, y) -> new int[]{y, x});
        verificar(6, true, (x, y) -> new int[]{y, H - 1 - x});         // la más común en fotos de teléfono
        verificar(7, true, (x, y) -> new int[]{W - 1 - y, H - 1 - x});
        verificar(8, true, (x, y) -> new int[]{W - 1 - y, x});
    }

    @Test
    void sinExifNoGira() {
        assertEquals(1, FotoPerfilService.orientacionExif(new byte[]{1, 2, 3}));
    }
}
