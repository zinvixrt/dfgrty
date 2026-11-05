package gcse.visuals.mixin;

import gcse.visuals.Fx;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Fog;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom fog color and distance (only in open air, so water/lava fog stays vanilla). */
@Mixin(BackgroundRenderer.class)
public class FogMixin {
    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true, require = 0)
    private static void ezka$applyFog(Camera camera, BackgroundRenderer.FogType fogType, Vector4f color,
                                      float viewDistance, boolean thickenFog, float tickDelta,
                                      CallbackInfoReturnable<Fog> cir) {
        if (!Fx.fogOn || camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        Fog f = cir.getReturnValue();
        int rgb = Fx.fogRgb(System.currentTimeMillis());
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float k = fogType == BackgroundRenderer.FogType.FOG_TERRAIN ? Fx.fogDist : 1.0f;
        cir.setReturnValue(new Fog(f.start() * k, f.end() * k, f.shape(), r, g, b, f.alpha()));
    }

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true, require = 0)
    private static void ezka$getFogColor(Camera camera, float tickDelta, ClientWorld world,
                                         int clampedViewDistance, float skyDarkness,
                                         CallbackInfoReturnable<Vector4f> cir) {
        if (!Fx.fogOn || camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        int rgb = Fx.fogRgb(System.currentTimeMillis());
        Vector4f v = cir.getReturnValue();
        cir.setReturnValue(new Vector4f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, v.w));
    }
}
