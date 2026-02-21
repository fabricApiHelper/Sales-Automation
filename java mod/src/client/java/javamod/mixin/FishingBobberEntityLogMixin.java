package javamod.mixin;

import net.minecraft.entity.projectile.FishingBobberEntity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityLogMixin {
	@Redirect(
		method = "onSpawnPacket",
		at = @At(
			value = "INVOKE",
			target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"
		)
	)
	private void javamod$silenceInvalidOwnerSpam(Logger logger, String message, Object arg1, Object arg2) {
		// Server-side plugins can spam invalid owner IDs for fishing hooks. Vanilla logs this at ERROR
		// and then discards the entity. We keep the discard behavior but silence the log spam.
	}
}


