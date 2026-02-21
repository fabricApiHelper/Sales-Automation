package javamod.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import javamod.SalesClientMod;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientScreenSuppressMixin {
	@Inject(method = "setScreen", at = @At("TAIL"))
	private void javamod$suppressScreenWhenSilentModeEnabled(Screen screen, CallbackInfo ci) {
		SalesClientMod.onClientScreenSet(screen);
	}
}

