package net.jj.giants.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.jj.giants.FirstJoin;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.GuideConfig;
import net.jj.giants.net.LiveNews;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The guide's game tests: the recipe, the book on first join, /giantsguide, its data and its lines, the live news. */
public class GuideGameTests implements FabricGameTest {
    /** every giant mod there is: the guide must have a page for each */
    private static final Set<String> GIANT_MODS = Set.of("mountain_breathes", "furrowmaw", "fire_ice_cerberus", "hollowbell", "lanternwillow", "wreckback");

    @SuppressWarnings("deprecation")
    private static ServerPlayer player(GameTestHelper h, UUID id) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, "gg-" + id.toString().substring(0, 8)), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        return p;
    }

    private static void leave(GameTestHelper h, ServerPlayer p) {
        h.getLevel().getServer().getPlayerList().remove(p);
    }

    private static int guides(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(GiantsGuideMod.GUIDE)) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void recipeExists(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        RecipeHolder<?> r = s.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("jj_giants", "giants_guide")).orElse(null);
        h.assertTrue(r != null, "the Giants Guide has a recipe");
        h.assertTrue(r.value() instanceof ShapelessRecipe, "the recipe is shapeless");
        h.assertTrue(r.value().getResultItem(h.getLevel().registryAccess()).is(GiantsGuideMod.GUIDE), "it makes the Giants Guide");
        // a book and a compass, in either order, in the crafting grid make one
        for (List<ItemStack> in : List.of(List.of(new ItemStack(Items.BOOK), new ItemStack(Items.COMPASS)),
                List.of(new ItemStack(Items.COMPASS), new ItemStack(Items.BOOK)))) {
            var got = s.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(2, 1, in), h.getLevel());
            h.assertTrue(got.isPresent() && got.get().value().getResultItem(h.getLevel().registryAccess()).is(GiantsGuideMod.GUIDE),
                    "a book and a compass make the guide");
        }
        var notJustABook = s.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(1, 1, List.of(new ItemStack(Items.BOOK))), h.getLevel());
        h.assertTrue(notJustABook.isEmpty() || !notJustABook.get().value().getResultItem(h.getLevel().registryAccess()).is(GiantsGuideMod.GUIDE),
                "a book on its own doesn't make it");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void firstJoinGivesOneBookOnce(GameTestHelper h) {
        boolean was = GuideConfig.V.giveOnFirstJoin;
        GuideConfig.V.giveOnFirstJoin = true;
        try {
            UUID id = UUID.randomUUID();
            ServerPlayer p = player(h, id);
            h.assertTrue(FirstJoin.get(h.getLevel().getServer()).had(id), "joining marked them as given");
            h.assertTrue(guides(p) == 1, "the first join gives exactly one guide (has " + guides(p) + ")");
            h.assertFalse(FirstJoin.onJoin(p), "joining again gives nothing");
            h.assertTrue(guides(p) == 1, "still one guide after joining again");
            leave(h, p);
            // they come back with an empty pack (a new character, a lost pack): still nothing new
            ServerPlayer back = player(h, id);
            back.getInventory().clearContent();
            h.assertFalse(FirstJoin.onJoin(back), "coming back gives nothing");
            h.assertTrue(guides(back) == 0, "no second guide");
            leave(h, back);
            // it's kept with the world
            CompoundTag tag = FirstJoin.get(h.getLevel().getServer()).save(new CompoundTag(), h.getLevel().registryAccess());
            boolean saved = false;
            for (var t : tag.getList("Given", 11)) saved |= net.minecraft.nbt.NbtUtils.loadUUID(t).equals(id);
            h.assertTrue(saved, "the world's save remembers them");
            // with the setting off, a new player gets none
            GuideConfig.V.giveOnFirstJoin = false;
            ServerPlayer other = player(h, UUID.randomUUID());
            h.assertTrue(guides(other) == 0, "with giveOnFirstJoin off nobody gets one");
            leave(h, other);
        } finally {
            GuideConfig.V.giveOnFirstJoin = was;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void giantsguideCommandGivesABook(GameTestHelper h) {
        ServerPlayer p = player(h, UUID.randomUUID());
        p.getInventory().clearContent();
        // any player, no cheats needed
        int r = h.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("giantsguide") == null ? 0 : 1;
        h.assertTrue(r == 1, "/giantsguide is there");
        h.getLevel().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(0), "giantsguide");
        h.assertTrue(guides(p) == 1, "/giantsguide gave one guide (has " + guides(p) + ")");
        h.getLevel().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(0), "giantsguide");
        h.assertTrue(guides(p) == 2, "and another each time");
        leave(h, p);
        h.succeed();
    }

    /** the guide's own language file, read straight from the jar */
    private static JsonObject lang() {
        try (var in = GuideGameTests.class.getResourceAsStream("/assets/jj_giants/lang/en_us.json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new RuntimeException("cannot read the guide's language file", e);
        }
    }

    private static boolean text(JsonObject l, String key) {
        return l.has(key) && !l.get(key).getAsString().isBlank();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyGiantHasHisPagesAndEveryMoveHasText(GameTestHelper h) {
        JsonObject l = lang();
        List<String> wrong = new ArrayList<>();
        for (String mod : GIANT_MODS) if (Giants.ALL.stream().noneMatch(g -> g.modId().equals(mod))) wrong.add("no entry for " + mod);
        int movesChecked = 0, ownChecked = 0;
        for (Giant g : Giants.ALL) {
            for (String k : new String[]{g.nameKey(), g.roleKey(), g.sizeKey(), "guide.jj_giants." + g.key() + ".short"})
                if (!text(l, k)) wrong.add("missing " + k);
            for (String page : Giants.PAGES)
                if (!page.equals("moves") && !text(l, g.lineKey(page, 1))) wrong.add("no " + page + " page for " + g.key());
            // (light, medium and heavy: every giant has them; only Wreckback has sea moves)
            for (int t = 0; t < 3; t++) {
                final int tier = t;
                if (g.moves().stream().noneMatch(m -> m.tier() == tier)) wrong.add(g.key() + " has no " + Giants.TIER_NAMES[t] + " moves");
            }
            boolean loaded = Giants.loaded(g);
            for (Giant.Move m : g.moves()) {
                movesChecked++;
                if (!text(l, g.moveNameCopy(m))) wrong.add("no name for " + g.key() + " " + m.id());
                if (!text(l, g.moveWhatCopy(m))) wrong.add("no text for " + g.key() + " " + m.id());
                // with his mod here, its own keys are the ones shown: they must be real
                if (loaded) {
                    ownChecked++;
                    if (!Language.getInstance().has(m.nameKey())) wrong.add(g.modId() + " has no " + m.nameKey());
                    if (!Language.getInstance().has(m.whatKey())) wrong.add(g.modId() + " has no " + m.whatKey());
                }
            }
            // every page line: items and recipes it names are real when his mod is here
            for (String page : Giants.PAGES)
                for (int n = 1; n <= Giants.MAX_LINES && l.has(g.lineKey(page, n)); n++) {
                    String s = l.get(g.lineKey(page, n)).getAsString();
                    checkLine(h, s, g.key() + "." + page + "." + n, wrong);
                }
        }
        for (String page : new String[]{"meetings", "giants"}) {
            if (!text(l, "guide.jj_giants.together." + page + ".1")) wrong.add("no together " + page + " page");
        }
        GiantsGuideMod.LOG.info("guide data: {} giants, {} moves, {} checked against the giants' own language files", Giants.ALL.size(), movesChecked, ownChecked);
        h.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
        h.succeed();
    }

    private static void checkLine(GameTestHelper h, String s, String where, List<String> wrong) {
        if (s.startsWith("@item ")) {
            for (String id : s.substring(6).split(" \\| ")[0].split(",")) {
                ResourceLocation rl = ResourceLocation.tryParse(id.trim());
                if (rl == null) { wrong.add(where + ": bad item " + id); continue; }
                if (FabricLoader.getInstance().isModLoaded(rl.getNamespace()) && BuiltInRegistries.ITEM.getOptional(rl).isEmpty())
                    wrong.add(where + ": no item " + rl);
            }
        } else if (s.startsWith("@recipe ")) {
            ResourceLocation rl = ResourceLocation.tryParse(s.substring(8).trim());
            if (rl == null) wrong.add(where + ": bad recipe");
            else if (FabricLoader.getInstance().isModLoaded(rl.getNamespace()) && h.getLevel().getServer().getRecipeManager().byKey(rl).isEmpty())
                wrong.add(where + ": no recipe " + rl);
        } else if (s.startsWith("@") && !Set.of("@picture", "@live", "@meettable").contains(s)) {
            wrong.add(where + ": unknown " + s);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void meetingTableWorksBothWays(GameTestHelper h) {
        for (Giant a : Giants.ALL) {
            h.assertFalse(Giants.fight(a, a), "a giant doesn't fight himself");
            for (Giant b : Giants.ALL) h.assertTrue(Giants.fight(a, b) == Giants.fight(b, a), a.key() + " and " + b.key() + " agree");
        }
        // the table every giant's own mod has (and its README): only these pairs fight
        String[][] fights = {{"pitchgut", "furrowmaw"}, {"pitchgut", "cerberus"}, {"furrowmaw", "cerberus"}, {"furrowmaw", "lanternwillow"},
                {"cerberus", "hollowbell"}, {"cerberus", "lanternwillow"},
                {"pitchgut", "wreckback"}, {"furrowmaw", "wreckback"}, {"cerberus", "wreckback"}};
        int n = 0;
        for (Giant a : Giants.ALL) for (Giant b : Giants.ALL) if (a.key().compareTo(b.key()) < 0 && Giants.fight(a, b)) n++;
        h.assertTrue(n == fights.length, "nine pairs fight (" + n + ")");
        for (String[] f : fights) h.assertTrue(Giants.fight(Giants.byKey(f[0]), Giants.byKey(f[1])), f[0] + " fights " + f[1]);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void liveNewsNeverBreaks(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        for (Giant g : Giants.ALL) {
            List<String> lines = LiveNews.ask(s, g);
            if (!Giants.loaded(g)) h.assertTrue(lines.isEmpty(), "no news about " + g.key() + " when his mod isn't here");
            else {
                h.assertTrue(!lines.isEmpty(), "his mod answers for " + g.key());
                GiantsGuideMod.LOG.info("live news {}: {}", g.key(), lines);
            }
        }
        // a bridge that isn't there (a giant not installed, or an old one) is just no news
        Giant fake = new Giant("nobody", "nobody", "nobody", "net.jj.nobody.GiantsBridge", List.of(), Set.of());
        h.assertTrue(LiveNews.bridge(s, fake, "where") == null, "a missing bridge is no news");
        h.succeed();
    }
}
