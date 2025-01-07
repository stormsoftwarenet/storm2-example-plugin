package net.storm.plugins.examples.task.tasks;

import net.storm.api.plugins.Task;
import net.storm.plugins.examples.task.ExampleTaskConfig;
import net.storm.sdk.game.Combat;
import net.storm.sdk.items.Inventory;

public class EatFood implements Task {
    private final ExampleTaskConfig config;

    public EatFood(ExampleTaskConfig config) {
        this.config = config;
    }

    @Override
    public boolean validate() {
        return config.eatFood() && Combat.getHealthPercent() < config.foodHp() && Inventory.contains(config.foodName());
    }

    @Override
    public int execute() {
        Inventory.getFirst(config.foodName())
                .interact("Eat");
        return 1200;
    }
}
