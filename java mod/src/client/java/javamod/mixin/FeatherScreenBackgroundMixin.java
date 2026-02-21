package javamod.mixin;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import javamod.ClientBrandingAssets;

/**
 * Feather uses its own Screen subclass (net.digitalingot.feather.az) for the title menu.
 * That class overrides renderBackground, so our generic Screen mixin does not run there.
 */
@Pseudo
@Mixin(targets = "net.digitalingot.feather.az", remap = false)
public abstract class FeatherScreenBackgroundMixin {
	@Inject(method = "method_25420(Lnet/minecraft/class_332;IIF)V", at = @At("HEAD"), cancellable = true, remap = false)
	private void javamod$renderFeatherBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		Object self = this;
		if (!ClientBrandingAssets.shouldRenderCustomBackground((net.minecraft.client.gui.screen.Screen) self)) {
			return;
		}
		if (ClientBrandingAssets.renderBackgroundCover(context, context.getScaledWindowWidth(), context.getScaledWindowHeight())) {
			ci.cancel();
		}
	}
}

