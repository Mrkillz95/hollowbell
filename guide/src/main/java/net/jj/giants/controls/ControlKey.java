package net.jj.giants.controls;

/**
 * One key mapping as the Controls tab sees it, without any client classes (so the game tests can make them): its
 * language key, its Controls category, and its key and default key as Minecraft writes them in options.txt
 * ("key.keyboard.r", "key.mouse.middle", "key.keyboard.unknown" for none).
 */
public record ControlKey(String name, String category, String key, String def) {
    public static final String NONE = "key.keyboard.unknown";

    public boolean unbound() {
        return key == null || key.isEmpty() || key.equals(NONE);
    }

    public boolean isDefault() {
        return key.equals(def);
    }
}
