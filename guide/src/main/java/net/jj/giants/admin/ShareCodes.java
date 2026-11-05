package net.jj.giants.admin;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 12, ADMIN_BRIDGE.md §29.3) Share codes: a whole preset or a world's settings as one short line to pass between
 * servers, made and read here (no website, nothing uploaded). {@code GS1-} + a check (six letters: the CRC-32 of the
 * settings) + the settings' JSON squeezed with a word list both ends know (the setting ids and the JSON's own words),
 * in URL-safe Base64. Reading one checks it whole (a wrong or missing letter is caught), and a code from a newer Guide
 * still reads: the settings this one doesn't know are left out, and the preview says which.
 */
public final class ShareCodes {
    private ShareCodes() {}

    public static final String PREFIX = "GS1-";
    private static final int CHECK = 6;

    /**
     * The word list (a deflate dictionary): fixed for GS1 for good, so a code made today reads the same forever. The
     * words most bundles use, the most common last (deflate finds the end of the list cheapest).
     */
    private static final byte[] WORDS = ("{\"admin\":1,\"preset\":\"\",\"name\":\"\",\"about\":\"\",\"replace\":false,"
            + "\"keep\":[\"worldgen\"],\"giants\":{},\"all\":{\"all.health\":\"all.damage\":\"all.speed\":"
            + "\"all.size\":\"all.cooldowns\":\"all.healing\":\"all.windCost\":\"all.myGiant\":true,"
            + "\"all.music.\"own\",\"off\",\"all.ambient.\"all.ambientVolume.\"all.voicePitch.\"all.herd\""
            + "\"all.growth\"\"all.hunger\"\"all.loyalty\"\"all.griefHeal\"\"all.deathRule\"\"all.weakPoints\""
            + "\"all.fear\"\"all.seasons\"\"all.villagers\"\"all.followers\"\"all.weather\"\"all.mirror\""
            + "\"all.chaos\"\"all.wildFightTo\"\"all.ownedFightTo\"\"all.paused\"\"all.quiet\"\"all.bossBars\""
            + "\"all.fightMusic\"\"usual\"\"move.\".cooldown\":\".wind\":\".on\":false,\"spawnSize\":["
            + "\"healthMultiplier\":\"damageMultiplier\":\"naturalSpawning\":\"worldScale\":\"griefing\":"
            + "\"startingMood\":\"hunting\"\"calm\"\"guardian\"\"minDistance\":\"maxDistance\":\"groundCopies\":"
            + "\"pitchgut\":{\"furrowmaw\":{\"cerberus\":{\"hollowbell\":{\"lanternwillow\":{\"wreckback\":{"
            + "\"swarmforge\":{\"gloamwick\":{\"replace\":true,\"giants\":{\"all\":{\"admin\":1,")
            .getBytes(StandardCharsets.UTF_8);

    /** a bundle as a share code */
    public static String encode(AdminTransfer.Bundle b) {
        byte[] raw = AdminTransfer.write(b).getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION, true);
        d.setDictionary(WORDS);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
        return PREFIX + e.encodeToString(check(raw)) + e.encodeToString(out.toByteArray());
    }

    /** the CRC-32 of the settings, as 4 bytes, and 6 letters once encoded */
    private static byte[] check(byte[] raw) {
        CRC32 c = new CRC32();
        c.update(raw);
        return ByteBuffer.allocate(4).putInt((int) c.getValue()).array();
    }

    /** what reading a code came to: the bundle (and a note for the preview), or why not */
    public record Read(@Nullable AdminTransfer.Bundle bundle, String why) {}

    /** is it a share code at all (any version) */
    public static boolean looksLike(@Nullable String text) {
        String t = text == null ? "" : text.trim();
        return t.length() > 4 && t.regionMatches(true, 0, "GS", 0, 2) && t.indexOf('-') > 2
                && t.substring(2, t.indexOf('-')).chars().allMatch(Character::isDigit);
    }

    public static Read read(@Nullable String text) {
        String t = text == null ? "" : text.trim().replaceAll("\\s+", "");
        if (!looksLike(t)) return new Read(null, "That isn't a share code: it starts with " + PREFIX);
        int dash = t.indexOf('-');
        int version = Integer.parseInt(t.substring(2, dash));
        if (version > 1)
            return new Read(null, "This code was made by a newer Giants Guide (" + t.substring(0, dash).toUpperCase(
                    Locale.ROOT) + "). Update the Guide on this server to read it.");
        String body = t.substring(dash + 1);
        if (body.length() <= CHECK) return new Read(null, "This code is too short: part of it is missing. Copy it"
                + " again.");
        byte[] want, packed;
        try {
            want = Base64.getUrlDecoder().decode(body.substring(0, CHECK));
            packed = Base64.getUrlDecoder().decode(body.substring(CHECK));
        } catch (IllegalArgumentException e) {
            return damaged();
        }
        byte[] raw;
        try {
            Inflater i = new Inflater(true);
            // (a raw stream: the word list is given before it starts)
            i.setDictionary(WORDS);
            i.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!i.finished()) {
                int n = i.inflate(buf);
                if (n == 0 && (i.needsInput() || i.needsDictionary())) break;
                out.write(buf, 0, n);
                if (out.size() > 1 << 20) break;
            }
            i.end();
            raw = out.toByteArray();
        } catch (DataFormatException | IllegalArgumentException e) {
            return damaged();
        }
        if (!java.util.Arrays.equals(want, check(raw))) return damaged();
        JsonObject o = AdminJson.object(new String(raw, StandardCharsets.UTF_8));
        if (o == null) return damaged();
        // (a newer Guide's settings: read as far as this one can; the preview lists what it leaves out)
        String note = "";
        try {
            if (o.has("admin") && o.get("admin").getAsInt() > AdminJson.VERSION) {
                o.add("admin", new JsonPrimitive(AdminJson.VERSION));
                note = " Made by a newer Giants Guide: what this one doesn't know is left out.";
            }
        } catch (RuntimeException e) {
            return damaged();
        }
        AdminTransfer.Bundle b = AdminTransfer.read(o.toString());
        if (b == null) return damaged();
        String about = (b.about().isEmpty() ? "" : b.about() + " ") + "From a share code." + note;
        return new Read(new AdminTransfer.Bundle(b.id().equals("import") ? "import" : b.id(), b.name(), about.trim(),
                b.replace(), b.values(), b.keep()), "");
    }

    private static Read damaged() {
        return new Read(null, "This code is damaged: a letter is wrong or missing. Copy it again from where it was"
                + " shared.");
    }
}
