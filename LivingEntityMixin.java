package gcse.visuals.mixin;

import gcse.visuals.Fx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Own hand swing duration (visual only, your player only). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
    private void ezka$swingDuration(CallbackInfoReturnable<Integer> cir) {
        if (Fx.swingOn && (Object) this == MinecraftClient.getInstance().player) {
            cir.setReturnValue(Fx.swingTicks);
        }
    }
}
