import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.Locale;

/**
 * Convierte cada letra de un texto a un trazado SVG usando una fuente TTF.
 * Uso: java GlyphPaths.java fuente.ttf TEXTO
 * Imprime un JSON con, por letra: d (trazado), limites visuales y avance, a tamano 100.
 * Las coordenadas son relativas a la linea base (y negativo = hacia arriba).
 */
public class GlyphPaths {

    public static void main(String[] args) throws Exception {
        Font font = Font.createFont(Font.TRUETYPE_FONT, new File(args[0])).deriveFont(100f);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        String text = args[1];
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < text.length(); i++) {
            GlyphVector gv = font.createGlyphVector(frc, text.substring(i, i + 1));
            Shape outline = gv.getOutline();
            Rectangle2D b = outline.getBounds2D();
            if (i > 0) {
                json.append(',');
            }
            json.append(String.format(Locale.ROOT,
                    "{\"ch\":\"%s\",\"d\":\"%s\",\"minX\":%.3f,\"minY\":%.3f,\"maxX\":%.3f,\"maxY\":%.3f,\"adv\":%.3f}",
                    text.charAt(i), toPath(outline), b.getMinX(), b.getMinY(), b.getMaxX(), b.getMaxY(),
                    gv.getGlyphMetrics(0).getAdvance()));
        }
        System.out.println(json.append(']'));
    }

    private static String toPath(Shape shape) {
        StringBuilder d = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> d.append(String.format(Locale.ROOT, "M%.2f %.2f", c[0], c[1]));
                case PathIterator.SEG_LINETO -> d.append(String.format(Locale.ROOT, "L%.2f %.2f", c[0], c[1]));
                case PathIterator.SEG_QUADTO -> d.append(String.format(Locale.ROOT, "Q%.2f %.2f %.2f %.2f",
                        c[0], c[1], c[2], c[3]));
                case PathIterator.SEG_CUBICTO -> d.append(String.format(Locale.ROOT, "C%.2f %.2f %.2f %.2f %.2f %.2f",
                        c[0], c[1], c[2], c[3], c[4], c[5]));
                case PathIterator.SEG_CLOSE -> d.append('Z');
                default -> {
                }
            }
        }
        return d.toString();
    }
}
