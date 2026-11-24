package net.jj.hollowbell.test;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Item tooltips stay short enough that no line runs off the screen. */
public class TooltipTests implements FabricGameTest {

    // about 270 of the 640 pixels across a 1080p screen at GUI scale 3, so a tooltip fits beside the cursor
    static final int LONGEST = 45;

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "tooltips")
    public void noTooltipLineIsTooLong(GameTestHelper h) {
        try (var in = TooltipTests.class.getResourceAsStream("/assets/hollowbell/lang/en_us.json")) {
            h.assertTrue(in != null, "no lang file");
            var lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            int lines = 0;
            for (var e : lang.entrySet()) {
                // item.hollowbell.<id> is the name; every key under an item or block is a tooltip line
                String[] k = e.getKey().split("\\.");
                if (k.length < 4 || !(k[0].equals("item") || k[0].equals("block"))) continue;
                lines++;
                String line = e.getValue().getAsString();
                h.assertTrue(line.length() <= LONGEST, line.length() + " long: " + e.getKey() + " = " + line);
            }
            h.assertTrue(lines > 0, "no tooltip lines found");
        } catch (IOException ex) {
            h.fail("couldn't read the lang file: " + ex);
        }
        h.succeed();
    }
}
