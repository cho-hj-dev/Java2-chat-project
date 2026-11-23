package view.style;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;

public final class EmoticonIcons {
	
	// 로그인 후 서버에서 받은 이미지를 보관한다.
	// DB 이미지가 없으면 기존 Java2D 아이콘을 계속 사용한다.
	private static final Map<String, ImageIcon> DB_ICONS = new HashMap<>();
	private static final Map<String, String> DB_LABELS = new HashMap<>();

    // 서버가 허용하는 ID와 반드시 같아야 한다.
    public static final List<String> IDS = List.of(
            "SMILE", "LAUGH", "WINK", "SAD", "ANGRY", "HEART");

    private static final Map<String, ImageIcon> ICONS = createIcons();

    private EmoticonIcons() {
    }

    public static ImageIcon iconFor(String id) {
        ImageIcon dbIcon = DB_ICONS.get(id);
        return dbIcon != null ? dbIcon : ICONS.get(id);
    }

    public static String labelFor(String id) {
        String dbLabel = DB_LABELS.get(id);
        if (dbLabel != null) {
            return dbLabel;
        }

        return switch (id) {
            case "SMILE" -> "웃음";
            case "LAUGH" -> "크게 웃음";
            case "WINK" -> "윙크";
            case "SAD" -> "슬픔";
            case "ANGRY" -> "화남";
            case "HEART" -> "하트";
            default -> "알 수 없음";
        };
    }

    public static List<String> availableIds() {
        // 기존 6개를 먼저 표시하고, 나중에 추가할 GIF ID도 포함한다.
        LinkedHashSet<String> ids = new LinkedHashSet<>(IDS);
        ids.addAll(DB_ICONS.keySet());
        return List.copyOf(ids);
    }

    public static void registerDbAsset(
            String id, String name, String mimeType, byte[] imageData) {

        if (!id.matches("[A-Z0-9_]{1,32}")
                || name.isBlank()
                || (!"image/png".equals(mimeType)
                    && !"image/gif".equals(mimeType))
                || imageData.length == 0
                || imageData.length > 1_048_576) {
            throw new IllegalArgumentException("잘못된 이모티콘 이미지 응답입니다.");
        }

        // ImageIcon은 GIF 바이트를 그대로 받으므로 애니메이션도 유지할 수 있다.
        ImageIcon icon = new ImageIcon(imageData);
        if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0
                || icon.getIconWidth() > 256
                || icon.getIconHeight() > 256) {
            throw new IllegalArgumentException("이모티콘 이미지 크기가 잘못됐습니다.");
        }

        DB_ICONS.put(id, icon);
        DB_LABELS.put(id, name);
    }

    public static int dbAssetCount() {
        return DB_ICONS.size();
    }

    private static Map<String, ImageIcon> createIcons() {
        Map<String, ImageIcon> icons = new HashMap<>();
        for (String id : IDS) {
            icons.put(id, drawIcon(id));
        }
        return Map.copyOf(icons);
    }

    private static ImageIcon drawIcon(String id) {
        BufferedImage image = new BufferedImage(
                52, 52, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        try {
            // 작은 크기에서도 곡선과 선이 매끄럽게 보이도록 한다.
            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(
                    2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            if ("HEART".equals(id)) {
                drawHeart(g);
            } else {
                drawFace(g, id);
            }
        } finally {
            g.dispose();
        }

        return new ImageIcon(image);
    }

    private static void drawHeart(Graphics2D g) {
        Path2D.Double heart = new Path2D.Double();
        heart.moveTo(26, 46);
        heart.curveTo(18, 40, 3, 28, 3, 17);
        heart.curveTo(3, 5, 18, 2, 26, 14);
        heart.curveTo(34, 2, 49, 5, 49, 17);
        heart.curveTo(49, 28, 34, 40, 26, 46);
        heart.closePath();

        g.setColor(new Color(245, 70, 105));
        g.fill(heart);
        g.setColor(new Color(175, 35, 65));
        g.draw(heart);
    }

    private static void drawFace(Graphics2D g, String id) {
        g.setColor(new Color(255, 211, 64));
        g.fillOval(3, 3, 46, 46);
        g.setColor(new Color(174, 115, 27));
        g.drawOval(3, 3, 46, 46);

        g.setColor(new Color(65, 45, 35));

        if ("LAUGH".equals(id)) {
            // 웃어서 접힌 눈
            g.drawLine(13, 18, 18, 22);
            g.drawLine(18, 22, 22, 18);
            g.drawLine(30, 18, 35, 22);
            g.drawLine(35, 22, 39, 18);
        } else {
            g.fillOval(15, 17, 5, 7);

            if ("WINK".equals(id)) {
                g.drawLine(31, 21, 39, 21);
            } else {
                g.fillOval(32, 17, 5, 7);
            }

            if ("ANGRY".equals(id)) {
                g.drawLine(12, 14, 21, 18);
                g.drawLine(31, 18, 40, 14);
            }
        }

        switch (id) {
            case "LAUGH" -> {
                g.fillOval(15, 28, 22, 12);
                g.setColor(new Color(235, 105, 125));
                g.fillOval(21, 35, 10, 4);
            }
            case "SAD", "ANGRY" ->
                    g.drawArc(14, 29, 24, 17, 0, 180);
            default ->
                    g.drawArc(13, 20, 26, 20, 180, 180);
        }
    }
}