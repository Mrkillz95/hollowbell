package net.jj.giants;

import java.util.List;
import java.util.Set;

/**
 * One giant, as the guide knows him. Everything the guide says about him is in its language file under
 * {@code guide.jj_giants.<key>.*}; this only holds what the code needs: his mod, his command, his bridge, his moves
 * and who he fights when giants meet.
 *
 * @param key     the guide's own short name for him (lang keys, pictures, /giants tp)
 * @param modId   his mod's id: the guide only shows him when this mod is loaded
 * @param command his own command, without the slash
 * @param bridge  his mod's GiantsBridge class, asked for live news (where he is, his settings)
 * @param moves   every move he has, light ones first
 * @param fights  the giants he fights when they meet (the table works both ways: a fight on either side is a fight)
 */
public record Giant(String key, String modId, String command, String bridge, List<Move> moves, Set<String> fights) {
    /**
     * @param id       the move's own name in his mod
     * @param tier     {@link Giants#LIGHT}, {@link Giants#MEDIUM} or {@link Giants#HEAVY}
     * @param nameKey  his mod's own lang key for the move's name
     * @param whatKey  his mod's own lang key for what it does (his book's tooltip)
     */
    public record Move(String id, int tier, String nameKey, String whatKey) {}

    /** the guide's own copy of a move's name, used when his mod doesn't have the key (an older one) */
    public String moveNameCopy(Move m) { return "guide.jj_giants." + key + ".move." + m.id(); }
    public String moveWhatCopy(Move m) { return "guide.jj_giants." + key + ".move." + m.id() + ".what"; }

    public String nameKey() { return "guide.jj_giants." + key + ".name"; }
    public String roleKey() { return "guide.jj_giants." + key + ".role"; }
    public String sizeKey() { return "guide.jj_giants." + key + ".size"; }
    /** the numbered lines of one of his pages: guide.jj_giants.KEY.PAGE.1, .2, ... */
    public String lineKey(String page, int n) { return "guide.jj_giants." + key + "." + page + "." + n; }
}
