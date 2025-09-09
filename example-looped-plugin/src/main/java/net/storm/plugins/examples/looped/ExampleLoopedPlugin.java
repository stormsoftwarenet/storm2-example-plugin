package net.storm.plugins.examples.looped;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.api.plugins.config.ConfigManager;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Combat;
import net.storm.sdk.items.Inventory;
import org.pf4j.Extension;

/*
 * A very basic example of a looped plugin.
 *
 * Important notes: look at the imports! The class names are similar to RuneLite's API, but they are not the same.
 * Always use the Storm SDK's classes when developing plugins.
 *
 * Ensure that your package names start with net.storm.plugins, or your plugin will not be compatible with the SDN.
 */
@PluginDescriptor(name = "Example Looped Plugin")
@Extension
public class ExampleLoopedPlugin extends LoopedPlugin {
    @Inject
    private ExampleLoopedConfig config;

    @Override
    public int loop() {
        if (config.eatFood() && Combat.getHealthPercent() < config.foodHp()) {
            var food = Inventory.getFirst(config.foodName());
            if (food != null) {
                food.interact("Eat");
                return 1200; // Eat, do not execute any other actions if eating, and wait for 1200 milliseconds
            }
        }

        var localPlayer = Players.getLocal();
        var npc = NPCs.query()
                .names(config.npcName())
                .results()
                .nearest(localPlayer);
        if (npc != null && !localPlayer.isInteracting() && npc.isInteractable()) {
            npc.interact("Attack");
        }

        return 1000; // Sleep for 1000 milliseconds
    }

    @Provides
    ExampleLoopedConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ExampleLoopedConfig.class);
    }
}
