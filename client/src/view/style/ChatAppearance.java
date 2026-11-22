package view.style;

import java.awt.Color;
import java.awt.Font;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public record ChatAppearance(
        Color backgroundColor,
        String fontFamily,
        int fontSize) {

    private static final String BACKGROUND_KEY = "backgroundRgb";
    private static final String FONT_FAMILY_KEY = "fontFamily";
    private static final String FONT_SIZE_KEY = "fontSize";

    private static final int DEFAULT_FONT_SIZE = 12;

    public ChatAppearance {
        if (backgroundColor == null
                || fontFamily == null
                || fontFamily.isBlank()
                || fontSize < 10
                || fontSize > 36) {
            throw new IllegalArgumentException("채팅 화면 설정값이 올바르지 않습니다.");
        }
    }

    public static ChatAppearance load() {
        try {
            // 이 PC의 현재 사용자 설정에 값을 보관한다.
            Preferences preferences =
                    Preferences.userNodeForPackage(ChatAppearance.class);

            int rgb = preferences.getInt(
                    BACKGROUND_KEY, Color.WHITE.getRGB());
            String family = preferences.get(
                    FONT_FAMILY_KEY, Font.DIALOG);
            int size = preferences.getInt(
                    FONT_SIZE_KEY, DEFAULT_FONT_SIZE);

            // 저장된 값이 손상됐더라도 기본 설정으로 화면을 연다.
            if (family.isBlank() || size < 10 || size > 36) {
                return defaults();
            }

            return new ChatAppearance(
                    new Color(rgb), family, size);
        } catch (SecurityException | IllegalArgumentException exception) {
            return defaults();
        }
    }

    public boolean save() {
        try {
            Preferences preferences =
                    Preferences.userNodeForPackage(ChatAppearance.class);

            preferences.putInt(BACKGROUND_KEY, backgroundColor.getRGB());
            preferences.put(FONT_FAMILY_KEY, fontFamily);
            preferences.putInt(FONT_SIZE_KEY, fontSize);
            preferences.flush();

            return true;
        } catch (BackingStoreException | SecurityException exception) {
            return false;
        }
    }

    public Font toFont() {
        return new Font(fontFamily, Font.PLAIN, fontSize);
    }

    public static ChatAppearance defaults() {
        return new ChatAppearance(
                Color.WHITE, Font.DIALOG, DEFAULT_FONT_SIZE);
    }
}