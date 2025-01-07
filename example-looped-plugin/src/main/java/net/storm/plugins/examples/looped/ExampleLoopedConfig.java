package net.storm.plugins.examples.looped;


import net.storm.api.plugins.SoxExclude;
import net.storm.api.plugins.config.Config;
import net.storm.api.plugins.config.ConfigGroup;
import net.storm.api.plugins.config.ConfigItem;

@ConfigGroup(ExampleLoopedConfig.GROUP)
@SoxExclude // Exclude from obfuscation
public interface ExampleLoopedConfig extends Config {
    String GROUP = "example-looped-plugin";

    @ConfigItem(
            keyName = "npcName",
            name = "NPC Name",
            description = "Name of the NPC to attack"
    )
    default String npcName() {
        return "Goblin";
    }

    @ConfigItem(
            keyName = "eatFood",
            name = "Eat Food?",
            description = "Eat food when low hp?"
    )
    default boolean eatFood() {
        return true;
    }

    @ConfigItem(
            keyName = "foodName",
            name = "Food Name",
            description = "Name of the food to eat"
    )
    default String foodName() {
        return "Lobster";
    }

    @ConfigItem(
            keyName = "foodHp",
            name = "Food HP %",
            description = "HP percentage to eat food at"
    )
    default int foodHp() {
        return 50;
    }
}
