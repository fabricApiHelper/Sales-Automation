package javamod.mixin;

import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import javamod.ClientBrandingAssets;
import net.minecraft.client.gui.screen.TitleScreen;

@Mixin(TitleScreen.class)
public abstract class TitleScreenCustomBackgroundMixin {
	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void javamod$renderCustomTitleBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (ClientBrandingAssets.renderBackgroundCover(context, context.getScaledWindowWidth(), context.getScaledWindowHeight())) {
			ci.cancel();
		}
	}
}

