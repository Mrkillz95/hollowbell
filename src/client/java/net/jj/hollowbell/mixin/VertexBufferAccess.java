package net.jj.hollowbell.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** a baked mesh's own index buffer, to put it back after drawing the glass in another one's order (Fast drawing) */
@Mixin(VertexBuffer.class)
public interface VertexBufferAccess {
    @Accessor("indexBufferId")
    int hollowbell$indexBufferId();
}
