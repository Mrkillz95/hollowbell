package net.jj.hollowbell.client.dev.mixin;

import com.mojang.blaze3d.shaders.Uniform;
import java.util.List;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (autotest drawcheck) a shader's uniforms */
@Mixin(ShaderInstance.class)
public interface ShaderUniforms {
    @Accessor("uniforms")
    List<Uniform> hollowbell$uniforms();
}
