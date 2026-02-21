package javamod;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaMod implements ModInitializer {
	public static final String MOD_ID = "javamod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Item RUBY = Registry.register(
		Registries.ITEM,
		Identifier.of(MOD_ID, "ruby"),
		new Item(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "ruby"))))
	);

	@Override
	public void onInitialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(RUBY));
		registerCommands();
		registerJoinMessage();

		LOGGER.info("Java Mod geladen.");
	}

	private static void registerCommands() {
		CommandRegistrationCallback.EVENT.register(JavaMod::registerJavamodCommand);
	}

	private static void registerJavamodCommand(
		com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher,
		CommandRegistryAccess registryAccess,
		CommandManager.RegistrationEnvironment environment
	) {
		dispatcher.register(CommandManager.literal("javamod")
			.then(CommandManager.literal("ping")
				.executes(context -> {
					context.getSource().sendFeedback(() -> Text.literal("javamod: pong"), false);
					return Command.SINGLE_SUCCESS;
				})
			)
		);
	}

	private static void registerJoinMessage() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			handler.player.sendMessage(Text.literal("Willkommen auf dem Server mit Java Mod!"), false)
		);
	}
}

