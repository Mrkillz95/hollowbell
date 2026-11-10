package net.jj.giants.test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * (lagfix, development only) What still holds on to an object (a minigame's world thrown away): a breadth-first walk
 * of the object graph from the server, the running threads and every static field of the giant mods and the Guide,
 * giving the shortest paths that reach it. Weak and soft references don't count (they keep nothing alive).
 */
public final class LeakScan {
    private LeakScan() {}

    static final Set<String> MODS = Set.of("jj_giants", "mountain_breathes", "furrowmaw", "cerberus", "hollowbell",
            "lanternwillow", "wreckback", "swarmforge", "gloamwick", "hearthstride");

    private record Node(Object o, int parent, String label) {}

    public static List<String> paths(MinecraftServer server, Object target, int most, int limit) {
        List<Node> nodes = new ArrayList<>();
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        List<String> out = new ArrayList<>();
        add(nodes, seen, queue, server, -1, "server");
        add(nodes, seen, queue, Thread.getAllStackTraces().keySet().toArray(), -1, "threads");
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            if (!MODS.contains(mod.getMetadata().getId())) continue;
            for (Path root : mod.getRootPaths()) {
                try (Stream<Path> s = Files.walk(root)) {
                    for (Path f : s.filter(x -> x.toString().endsWith(".class")).toList()) {
                        String n = root.relativize(f).toString().replace('\\', '/').replace('/', '.');
                        n = n.substring(0, n.length() - 6);
                        if (n.contains(".client.") || n.contains("mixin") || n.contains("module-info")) continue;
                        Class<?> c;
                        try {
                            c = Class.forName(n, false, LeakScan.class.getClassLoader());
                        } catch (Throwable t) {
                            continue;
                        }
                        try {
                            for (Field fl : c.getDeclaredFields()) {
                                if (!Modifier.isStatic(fl.getModifiers()) || fl.getType().isPrimitive()) continue;
                                try {
                                    fl.setAccessible(true);
                                    add(nodes, seen, queue, fl.get(null), -1, c.getName() + "." + fl.getName());
                                } catch (Throwable t) {
                                    // (one we can't read)
                                }
                            }
                        } catch (Throwable t) {
                            // (a class that can't be looked at)
                        }
                    }
                } catch (Exception e) {
                    // (a root that can't be walked)
                }
            }
        }
        while (!queue.isEmpty() && nodes.size() < limit && out.size() < most) {
            int i = queue.poll();
            Object o = nodes.get(i).o();
            if (o == target) {
                out.add(path(nodes, i));
                continue;
            }
            if (o instanceof java.lang.ref.Reference<?> || o instanceof Class<?> || o instanceof ClassLoader
                    || o instanceof String) continue;
            try {
                if (o instanceof Object[] a) {
                    for (int k = 0; k < a.length; k++) add(nodes, seen, queue, a[k], i, "[" + k + "]");
                    continue;
                }
                if (o.getClass().isArray()) continue;
                if (o instanceof Map<?, ?> m && o.getClass().getName().startsWith("java.")) {
                    for (var en : List.copyOf(m.entrySet())) {
                        add(nodes, seen, queue, en.getKey(), i, "{key " + brief(en.getKey()) + "}");
                        add(nodes, seen, queue, en.getValue(), i, "{" + brief(en.getKey()) + "}");
                    }
                    continue;
                }
                if (o instanceof Collection<?> c && o.getClass().getName().startsWith("java.")) {
                    int k = 0;
                    for (Object x : List.copyOf(c)) add(nodes, seen, queue, x, i, "<" + k++ + ">");
                    continue;
                }
                for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Field fl : c.getDeclaredFields()) {
                        if (Modifier.isStatic(fl.getModifiers()) || fl.getType().isPrimitive()) continue;
                        try {
                            fl.setAccessible(true);
                            add(nodes, seen, queue, fl.get(o), i, "." + fl.getName());
                        } catch (Throwable t) {
                            // (java.* internals: not opened to us)
                        }
                    }
                }
            } catch (Throwable t) {
                // (changed while we looked)
            }
        }
        if (out.isEmpty()) out.add("no path found in " + nodes.size() + " objects");
        return out;
    }

    private static void add(List<Node> nodes, IdentityHashMap<Object, Boolean> seen, ArrayDeque<Integer> q, Object o,
            int parent, String label) {
        if (o == null || seen.put(o, Boolean.TRUE) != null) return;
        nodes.add(new Node(o, parent, label));
        q.add(nodes.size() - 1);
    }

    private static String brief(Object o) {
        String s;
        try {
            s = String.valueOf(o);
        } catch (Throwable t) {
            s = o.getClass().getSimpleName();
        }
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private static String path(List<Node> nodes, int i) {
        List<String> parts = new ArrayList<>();
        for (int k = i; k >= 0; k = nodes.get(k).parent()) {
            Node n = nodes.get(k);
            parts.add(0, n.label() + "(" + n.o().getClass().getSimpleName() + ")");
        }
        return String.join(" ", parts);
    }
}
