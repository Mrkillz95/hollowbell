package net.jj.hollowbell.command;

import net.jj.hollowbell.HollowbellConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every setting in config/hollowbell.json, by its name, for /hollowbell config and /giants config: read one, or set
 * it (checked and saved). Numbers keep to the same limits the file does when it's read.
 */
public final class Settings {
    private Settings() {}

    /** every setting's name, in the file's order (the old renamed ones left out) */
    public static List<String> keys() {
        List<String> out = new ArrayList<>();
        for (Field f : HollowbellConfig.Values.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getName().equals("configVersion") || f.getName().equals("maxHollowbells")) continue;
            out.add(f.getName());
        }
        return out;
    }

    private static Field field(String key) {
        for (Field f : HollowbellConfig.Values.class.getFields())
            if (f.getName().equalsIgnoreCase(key) && keys().contains(f.getName())) return f;
        return null;
    }

    public static boolean has(String key) { return field(key) != null; }

    /** what it's set to now, or null when there's no such setting */
    public static String get(String key) {
        Field f = field(key);
        if (f == null) return null;
        try { return String.valueOf(f.get(HollowbellConfig.V)); } catch (IllegalAccessException e) { return null; }
    }

    /** sets it; returns null when it worked, or what was wrong */
    public static String set(String key, String value) {
        Field f = field(key);
        if (f == null) return "there's no setting called " + key;
        String v = value.trim().toLowerCase(Locale.ROOT);
        try {
            Class<?> t = f.getType();
            if (t == boolean.class) {
                boolean b = switch (v) {
                    case "on", "true", "yes" -> true;
                    case "off", "false", "no" -> false;
                    default -> throw new IllegalArgumentException("on or off");
                };
                f.setBoolean(HollowbellConfig.V, b);
            } else if (t == int.class) f.setInt(HollowbellConfig.V, Integer.parseInt(v));
            else if (t == float.class) f.setFloat(HollowbellConfig.V, Float.parseFloat(v));
            else if (t == double.class) f.setDouble(HollowbellConfig.V, Double.parseDouble(v));
            else return "that one can't be set from a command";
        } catch (NumberFormatException e) {
            return key + " needs a number";
        } catch (IllegalArgumentException e) {
            return key + " needs " + e.getMessage();
        } catch (IllegalAccessException e) {
            return "that one can't be set from a command";
        }
        // the same limits as reading the file
        HollowbellConfig.migrate(HollowbellConfig.V);
        HollowbellConfig.save();
        return null;
    }
}
