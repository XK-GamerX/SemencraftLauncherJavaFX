import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Genera las imagenes del instalador MSI (WiX) a partir de los assets del launcher.
 *
 *   dialog.bmp  493x312  Bienvenida y final: panel izquierdo con arte y logo; a la derecha
 *                        va el texto de Windows (negro), por eso esa zona queda blanca.
 *   banner.bmp  493x58   Cabecera del resto de pasos: blanca a la izquierda (titulo en negro)
 *                        y el arte con el icono a la derecha.
 *
 * Uso (desde la raiz del proyecto): java design/installer/InstallerArt.java
 */
public class InstallerArt {

    static final String ASSETS = "src/main/resources/com/semencraft/semencraftlauncherjavafx/assets/";
    static final String OUT = "installer/wix/";

    static final Color NIGHT_TOP = new Color(0x2b, 0x23, 0x42);
    static final Color NIGHT_BOTTOM = new Color(0x0f, 0x0d, 0x16);
    static final Color ACCENT = new Color(0xff, 0xb5, 0x47);

    public static void main(String[] args) throws Exception {
        BufferedImage scene = ImageIO.read(new File(ASSETS + "bg.png"));
        BufferedImage logo = ImageIO.read(new File(ASSETS + "logo-semencraft.png"));
        BufferedImage icon = ImageIO.read(new File(ASSETS + "icon.png"));

        writeBmp(dialog(scene, logo), OUT + "dialog.bmp");
        writeBmp(banner(scene, icon), OUT + "banner.bmp");
    }

    static BufferedImage dialog(BufferedImage scene, BufferedImage logo) {
        int w = 493, h = 312, panel = 170;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = start(img);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);

        // Arte: una franja vertical de la escena (el sol queda arriba) tenida de noche
        drawCover(g, scene, 0, 0, panel, h, 0.36);
        g.setPaint(new GradientPaint(0, 0, alpha(NIGHT_TOP, 0.30f), 0, h, alpha(NIGHT_BOTTOM, 0.92f)));
        g.fillRect(0, 0, panel, h);

        int logoW = 124;
        int logoH = Math.round(logo.getHeight() * logoW / (float) logo.getWidth());
        g.drawImage(logo, (panel - logoW) / 2, 92, logoW, logoH, null);

        // Filete de acento que separa el arte de la zona de texto
        g.setColor(ACCENT);
        g.fillRect(panel, 0, 3, h);
        g.dispose();
        return img;
    }

    static BufferedImage banner(BufferedImage scene, BufferedImage icon) {
        int w = 493, h = 58, art = 150;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = start(img);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);

        int x0 = w - art;
        BufferedImage strip = new BufferedImage(art, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D s = start(strip);
        drawCover(s, scene, 0, 0, art, h, 0.40);
        s.setColor(alpha(NIGHT_BOTTOM, 0.55f));
        s.fillRect(0, 0, art, h);
        int iconSize = 34;
        s.drawImage(icon, art - iconSize - 14, (h - iconSize) / 2, iconSize, iconSize, null);
        // Se desvanece hacia la izquierda para no pisar el texto largo de la descripcion
        s.setComposite(AlphaComposite.DstIn);
        s.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 0), art * 0.55f, 0, Color.BLACK));
        s.fillRect(0, 0, art, h);
        s.dispose();
        g.drawImage(strip, x0, 0, null);
        g.dispose();
        return img;
    }

    /** Dibuja un recorte de la imagen que llena el rectangulo (tipo "cover"), centrado en focusX. */
    static void drawCover(Graphics2D g, BufferedImage src, int x, int y, int w, int h, double focusX) {
        double scale = Math.max(w / (double) src.getWidth(), h / (double) src.getHeight());
        int cropW = (int) Math.round(w / scale);
        int cropH = (int) Math.round(h / scale);
        int sx = (int) Math.round(Math.max(0, Math.min(src.getWidth() - cropW, src.getWidth() * focusX - cropW / 2.0)));
        int sy = (src.getHeight() - cropH) / 2;
        g.drawImage(src, x, y, x + w, y + h, sx, sy, sx + cropW, sy + cropH, null);
    }

    static Graphics2D start(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }

    static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(a * 255));
    }

    static void writeBmp(BufferedImage img, String path) throws Exception {
        if (!ImageIO.write(img, "bmp", new File(path))) {
            throw new IllegalStateException("No se pudo escribir " + path);
        }
        System.out.println("escrito " + path + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }
}
