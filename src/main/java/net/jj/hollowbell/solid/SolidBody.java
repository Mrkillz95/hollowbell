package net.jj.hollowbell.solid;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * The adapter a creature writes to be solid (the only creature-specific part of the kit). He's made of bones; each
 * bone's pose is a matrix from his model as built to his model now (before he is turned and sized). The kit turns
 * that into the world itself: his position, then {@link #solidRoot} (his turn and size), then the bone.
 *
 * Implement it on the entity, keep a {@code final SolidCache solidCache = new SolidCache();} field, call
 * {@link Solid#track} each tick, {@link Solid#serverTick} at the end of his server tick, {@link Solid#untrack} when he
 * goes, and {@link SolidCache#touch} whenever his pose has been worked out again.
 */
public interface SolidBody {
    /** the entity (usually this) */
    Entity solidSelf();

    /** what of him is solid, as built (made once per type) */
    SolidShape solidShape();

    /** his own per-tick store, kept on the entity */
    SolidCache solidCache();

    /** solid at all now: there, alive, big enough, his pose known, not burrowed... */
    boolean solidReady();

    /** a box round all of him in the world (where to look at all; may be generous) */
    AABB solidBox();

    /** the pose as of this tick, by bone (never changed by the kit) */
    Matrix4f[] solidPose();

    /** the pose between the last tick and this one, as drawn (partial 0..1), into out (by bone) */
    void solidPoseAt(float partial, Matrix4f[] out);

    /** his turn and size at a moment between ticks (no position): model space to world, about his feet */
    Matrix4f solidRoot(float partial, Matrix4f out);

    /** is this bone there now (not broken off, not growing back, not hidden) */
    boolean solidBoneOn(int bone);

    /** how big his blocks are in the world (his size) */
    float solidScale();

    /** what never bumps into him: his own seats, shots, hooks, whoever rides at his helm... */
    default boolean solidIgnores(Entity e) { return false; }

    /**
     * The bone that carries whoever stands on this one: itself, or (for a part that rolls or swells under you, like an
     * eye) a steadier one it hangs from. It must be solid too.
     */
    default int solidCarryBone(int bone) { return bone; }

    /** a bump this high over your feet (world blocks) you walk up onto without jumping */
    default double solidStep() { return 1.3; }
}
