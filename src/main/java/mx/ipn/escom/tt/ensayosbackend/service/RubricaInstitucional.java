package mx.ipn.escom.tt.ensayosbackend.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Descripciones de la Tabla 14 de TT2 (Rúbrica de Calificación) por criterio y nivel. Sustituyen al
 * detalle técnico del motor de IA ("Evaluado por CU-IA-...") en el reporte que ven alumno y profesor.
 * En Referencias se admite IEEE o APA, como indica la nota posterior a la Tabla 14.
 */
public final class RubricaInstitucional {

    public enum Nivel { ALTO, MEDIO, BAJO }

    private record Descripciones(String alto, String medio, String bajo) {
        String de(Nivel nivel) {
            return switch (nivel) {
                case ALTO -> alto;
                case MEDIO -> medio;
                case BAJO -> bajo;
            };
        }
    }

    private static final Map<String, Descripciones> TABLA_14 = Map.of(
            "estructura documental", new Descripciones(
                    "El ensayo presenta una estructura lógica impecable, distinguiendo claramente mediante "
                            + "encabezados las cuatro secciones fundamentales: Título, Introducción, Desarrollo, "
                            + "Conclusiones y Referencias.",
                    "Presenta una estructura básica, pero la separación entre secciones no es totalmente clara, "
                            + "omite algún encabezado o el orden lógico presenta ligeras inconsistencias.",
                    "El documento carece de orden lógico, no separa las secciones o faltan apartados obligatorios "
                            + "para la comprensión del texto."),
            "formato de archivo", new Descripciones(
                    "El archivo cumple estrictamente con las normas de envío: formato PDF legible (texto "
                            + "seleccionable) y nombrado correctamente según el protocolo (Grupo_Nombre).",
                    "El archivo se entrega en formato PDF legible, pero no cumple con el protocolo de "
                            + "nomenclatura o presenta problemas menores de metadatos.",
                    "El archivo se entrega en formato incorrecto (Word, Imagen), está dañado, no se puede leer el "
                            + "texto o no respeta la nomenclatura solicitada."),
            "ortografia y sintaxis", new Descripciones(
                    "Redacción académica fluida y profesional en prosa continua. La ortografía y gramática son "
                            + "impecables, facilitando la lectura y la comprensión de las ideas.",
                    "Redacción comprensible en prosa, pero presenta errores ortográficos o gramaticales moderados "
                            + "que no impiden la lectura, o incluye un uso ocasional e innecesario de listas.",
                    "El texto presenta múltiples errores ortográficos o gramaticales que dificultan la lectura. "
                            + "Uso excesivo de listas o viñetas en lugar de párrafos argumentativos."),
            "titulo del ensayo", new Descripciones(
                    "El título es original, creativo y refleja con precisión el contenido temático del ensayo "
                            + "sobre \"Sistemas Operativos\".",
                    "El título está presente y aborda el tema, pero carece de originalidad, es demasiado general "
                            + "o no refleja con total precisión el contenido del ensayo.",
                    "El ensayo no tiene título, o este es genérico, incoherente o no guarda relación con el "
                            + "contenido abordado."),
            "introduccion", new Descripciones(
                    "La introducción tiene una extensión adecuada. Plantea claramente el objetivo del ensayo, "
                            + "contextualiza el tema y logra captar el interés del lector.",
                    "Presenta una introducción al tema, pero su extensión es desproporcionada (corta o larga) o el "
                            + "objetivo central del ensayo no queda planteado con total claridad.",
                    "La introducción es excesivamente breve o irrelevante; no plantea el propósito del escrito ni "
                            + "introduce al tema central."),
            "desarrollo", new Descripciones(
                    "Es la sección principal del texto. Demuestra un dominio profundo de los temas clave. La "
                            + "argumentación es sólida, coherente y está sustentada mediante citas bibliográficas "
                            + "en el cuerpo del texto.",
                    "Aborda los temas clave pero la argumentación es algo superficial o presenta saltos lógicos. "
                            + "Incluye información pertinente, pero las citas en el texto son escasas o están mal "
                            + "integradas.",
                    "El desarrollo es superficial, breve o divaga fuera del tema. Carece de citas que sustenten "
                            + "las afirmaciones o se detecta copia textual sin atribución (plagio)."),
            "conclusiones", new Descripciones(
                    "Presenta un cierre contundente que sintetiza los puntos principales y ofrece una reflexión "
                            + "personal o interpretación final sobre el tema. Extensión equilibrada.",
                    "Ofrece un resumen básico de las ideas expuestas, pero carece de una reflexión personal "
                            + "profunda o el cierre se percibe apresurado y poco analítico.",
                    "El ensayo termina abruptamente sin una conclusión clara, o esta es irrelevante y no aporta un "
                            + "cierre a las ideas expuestas."),
            "referencias", new Descripciones(
                    "Incluye un listado final de fuentes de información confiables. Aplica correctamente el "
                            + "estándar IEEE o APA tanto en la bibliografía como en las citas dentro del texto.",
                    "Incluye referencias de fuentes aceptables, pero presenta inconsistencias en la aplicación del "
                            + "formato IEEE o APA o no todas las fuentes listadas están citadas en el texto.",
                    "No incluye referencias bibliográficas, las fuentes no son confiables o el formato de citación "
                            + "es erróneo o inexistente."));

    private RubricaInstitucional() {
    }

    /** Nivel Alto (100 %), Medio (parcial) o Bajo (0 %) según el puntaje obtenido. */
    public static Nivel nivel(BigDecimal obtenido, BigDecimal maximo) {
        if (obtenido.compareTo(maximo) >= 0) {
            return Nivel.ALTO;
        }
        return obtenido.signum() <= 0 ? Nivel.BAJO : Nivel.MEDIO;
    }

    /** Descripción de la Tabla 14, o null si el criterio no es uno de los ocho de la rúbrica. */
    public static String descripcion(String criterio, Nivel nivel) {
        Descripciones d = TABLA_14.get(normalizar(criterio));
        return d == null ? null : d.de(nivel);
    }

    // "Título del Ensayo" → "titulo del ensayo": el motor podría variar acentos o mayúsculas
    private static String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
