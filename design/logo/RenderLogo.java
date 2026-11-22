import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Rasteriza un SVG aplanado de generate_logos.py (un solo path con M/L/Z) a PNG transparente.
 * Uso: java RenderLogo.java entrada.svg salida.png anchoEnPx
 */
public class RenderLogo {

    public static void main(String[] args) throws Exception {
        String svg = Files.readString(new File(args[0]).toPath());
        int targetWidth = Integer.parseInt(args[2]);

        Matcher viewBox = Pattern.compile("viewBox=\"0 0 ([\\d.]+) ([\\d.]+)\"").matcher(svg);
        Matcher fill = Pattern.compile("fill=\"#([0-9a-fA-F]{6})\"").matcher(svg);
        Matcher stroke = Pattern.compile("stroke-width=\"([\\d.]+)\"").matcher(svg);
        Matcher data = Pattern.compile(" d=\"([^\"]+)\"").matcher(svg);
        if (!viewBox.find() || !fill.find() || !stroke.find() || !data.find()) {
            throw new IllegalArgumentException("SVG inesperado: " + args[0]);
        }
        double vbWidth = Double.parseDouble(viewBox.group(1));
        double vbHeight = Double.parseDouble(viewBox.group(2));
        double scale = targetWidth / vbWidth;

        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        Matcher cmd = Pattern.compile("([MLZ])([^MLZ]*)").matcher(data.group(1));
        while (cmd.find()) {
            String[] nums = cmd.group(2).trim().isEmpty() ? new String[0] : cmd.group(2).trim().split("\\s+");
            switch (cmd.group(1)) {
                case "M" -> path.moveTo(Double.parseDouble(nums[0]), Double.parseDouble(nums[1]));
                case "L" -> path.lineTo(Double.parseDouble(nums[0]), Double.parseDouble(nums[1]));
                default -> path.closePath();
            }
        }

        BufferedImage image = new BufferedImage(targetWidth, (int) Math.ceil(vbHeight * scale), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.transform(AffineTransform.getScaleInstance(scale, scale));
        g.setColor(new Color(Integer.parseInt(fill.group(1), 16)));
        g.fill(path);
        g.setStroke(new BasicStroke((float) Double.parseDouble(stroke.group(1)), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(path);
        g.dispose();
        ImageIO.write(image, "png", new File(args[1]));
        System.out.println("PNG " + image.getWidth() + "x" + image.getHeight() + " -> " + args[1]);
    }
}
