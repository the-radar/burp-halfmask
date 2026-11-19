package net.theradar.halfmask;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Renders a masked request and response side by side into a PNG, the way a report figure is laid out.
 * Pure Swing painting into an off-screen image, so it works without taking a screenshot of the Burp window.
 */
public final class PngExporter {
    private static final int SCALE = 2;              // retina-grade output
    private static final int MAX_COLS = 110;          // wrap long lines so the figure stays readable in a report
    private static final int MAX_LINES = 90;          // keep the figure to roughly one page

    public static void export(String maskedRequest, String maskedResponse, String title, File out) throws IOException {
        export(maskedRequest, maskedResponse, title, out, false, MAX_LINES);
    }

    /** stacked puts the request above the response, which doubles the text size when the figure is fitted to a page width. */
    public static void export(String maskedRequest, String maskedResponse, String title, File out, boolean stacked, int maxLines) throws IOException {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, stacked ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(pane("Request" + (title == null ? "" : "  " + title), maskedRequest, maxLines));
        root.add(stacked ? javax.swing.Box.createVerticalStrut(10) : javax.swing.Box.createHorizontalStrut(14));
        root.add(pane("Response", maskedResponse, maxLines));
        root.setSize(root.getPreferredSize());
        layout(root);
        Dimension d = root.getSize();
        BufferedImage img = new BufferedImage(Math.max(1, d.width * SCALE), Math.max(1, d.height * SCALE), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(SCALE, SCALE);
        root.print(g);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    private static JPanel pane(String heading, String body, int maxLines) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(Color.WHITE);
        JLabel h = new JLabel(heading);
        h.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        h.setForeground(Color.BLACK);
        JTextArea ta = new JTextArea(clip(body == null ? "" : body, maxLines));
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        ta.setLineWrap(true); ta.setWrapStyleWord(false); ta.setEditable(false);
        ta.setBackground(new Color(0xF7, 0xF7, 0xF7));
        ta.setForeground(Color.BLACK);
        ta.setDisabledTextColor(Color.BLACK);
        ta.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xCC, 0xCC, 0xCC)), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        int charW = ta.getFontMetrics(ta.getFont()).charWidth('M');
        ta.setSize(new Dimension(charW * MAX_COLS + 16, Integer.MAX_VALUE));
        ta.setPreferredSize(new Dimension(charW * MAX_COLS + 16, ta.getPreferredSize().height));
        p.add(h, BorderLayout.NORTH); p.add(ta, BorderLayout.CENTER);
        return p;
    }

    private static String clip(String s, int maxLines) {
        String[] lines = s.split("\r?\n", -1);
        if (lines.length <= maxLines) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) sb.append(lines[i]).append('\n');
        sb.append("... (").append(lines.length - maxLines).append(" more lines)");
        return sb.toString();
    }

    private static void layout(java.awt.Component c) {
        c.doLayout();
        if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) layout(k);
    }
}
