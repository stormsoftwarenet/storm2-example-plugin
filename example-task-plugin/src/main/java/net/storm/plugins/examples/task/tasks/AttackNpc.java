package net.storm.plugins.examples.task.tasks;

import net.storm.api.plugins.Task;
import net.storm.plugins.examples.task.ExampleTaskConfig;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;

public class AttackNpc implements Task {
    private final ExampleTaskConfig config;

    public AttackNpc(ExampleTaskConfig config) {
        this.config = config;
    }

    @Override
    public boolean validate() {
        return true; // always execute this task, if the NPC is not available, just idle
    }

    @Override
    public int execute() {
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
}
