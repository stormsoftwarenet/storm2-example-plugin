package steps;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.storm.plugins.tutorialisland.JGTutorialIslandPlugin;
import net.storm.plugins.tutorialisland.JGTutorialIslandState;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Widgets;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.items.Inventory;
import net.storm.api.domain.items.IInventoryItem;

@Slf4j
public class MasterChefStep implements TutorialStep {
    private final JGTutorialIslandPlugin plugin;

    private static final int MASTER_CHEF_ID = 3305; // Confirm with Dev Tools
    private static final int RANGE_ID = 9736;       // Confirm with Dev Tools
    private static final int MASTER_CHEF_TILE_X = 3075;
    private static final int MASTER_CHEF_TILE_Y = 3085;

    private static final String[] FLOUR_NAMES = {"Pot of flour"};
    private static final String[] WATER_NAMES = {"Bucket of water"};
    private static final String[] DOUGH_NAMES = {"Bread dough"};
    private static final String[] BREAD_NAMES = {"Bread"};

    private static final int MUSIC_WIDGET_PARENT = 261;
    private static final int MUSIC_WIDGET_CHILD = 1;

    private enum SubState {
        WALK_TO_CHEF,
        TALK_CHEF,
        GET_INGREDIENTS,
        MIX_DOUGH,
        BAKE_BREAD,
        MUSIC_PLAYER,
        MOVE_ON
    }
    @Getter
    private SubState subState;

    public MasterChefStep(JGTutorialIslandPlugin plugin) {
        this.plugin = plugin;
        // Default to TALK_CHEF, never query the world here!
        this.subState = SubState.WALK_TO_CHEF;
    }

    private void setSubState(SubState next) {
        if (subState != next) log.info("Changing substate: {} -> {}", subState, next);
        subState = next;
    }

    @Override
    public boolean validate() {
        boolean valid = plugin.getCurrentState() == JGTutorialIslandState.MASTER_CHEF;
        log.info("Validate called: currentState={}, valid={}", plugin.getCurrentState(), valid);
        return valid;
    }

    @Override
    public int execute() {
        plugin.incrementTick();

        // Only re-infer when not in dialog or using an item (robust restarts)
        if (!Dialog.isOpen() && !Players.getLocal().isInteracting()) {
            SubState newInferred = inferSubState();
            if (newInferred != subState) {
                log.info("Auto-correcting subState: {} -> {}", subState, newInferred);
                setSubState(newInferred);
            }
        }
        log.info("---- MasterChefStep Tick ---- Current subState: {}", subState);

        switch (subState) {
            case WALK_TO_CHEF: {
                INPC chef = getChef();
                if (chef == null) {
                    log.warn("Can't find Master Chef NPC, walking to fallback tile ({}, {})", MASTER_CHEF_TILE_X, MASTER_CHEF_TILE_Y);
                    Movement.walkTo(MASTER_CHEF_TILE_X, MASTER_CHEF_TILE_Y);
                    return randomDelay(1000);
                }
                if (!isAt(chef)) {
                    Movement.walkTo(chef.getWorldLocation());
                    log.info("Walking to Master Chef at {}", chef.getWorldLocation());
                    return randomDelay(1000);
                }
                log.info("Arrived at Master Chef. Switching to TALK_CHEF");
                setSubState(SubState.TALK_CHEF);
                return randomDelay(600);
            }

            case TALK_CHEF: {
                INPC chefTalk = getChef();
                // If we have both flour and water, proceed
                if (hasFlour() && hasWater()) {
                    setSubState(SubState.MIX_DOUGH);
                    log.info("Both flour and water detected; switching to MIX_DOUGH.");
                    return randomDelay(600);
                }
                // If options/dialogue, handle as normal
                if (Dialog.isViewingOptions()) {
                    Dialog.chooseOption(1);
                    log.info("Choosing dialog option 1 for chef.");
                    return randomDelay(400);
                }
                if (Dialog.isOpen() && Dialog.canContinue()) {
                    Dialog.continueSpace();
                    log.info("Continuing dialog with chef.");
                    return randomDelay(200);
                }
                // If either flour or water missing, try to talk
                if (chefTalk != null && isAt(chefTalk)) {
                    chefTalk.interact("Talk-to");
                    log.info("Talking to Master Chef for bread instructions/ingredients.");
                    return randomDelay(1200);
                }
                return randomDelay(600);
            }

            case GET_INGREDIENTS: {
                // Only talk if missing any required item
                if (!hasFlour() || !hasWater()) {
                    INPC chef2 = getChef();
                    if (chef2 != null && isAt(chef2)) {
                        chef2.interact("Talk-to");
                        log.info("Getting more flour/water from chef.");
                        return randomDelay(1200);
                    }
                } else {
                    setSubState(SubState.MIX_DOUGH);
                    log.info("Obtained flour and water. Switching to MIX_DOUGH.");
                }
                return randomDelay(600);
            }

            case MIX_DOUGH: {
                // If missing dough, try to combine ingredients
                if (!hasDough()) {
                    if (!hasFlour() || !hasWater()) {
                        log.info("Missing flour/water, switching to GET_INGREDIENTS.");
                        setSubState(SubState.GET_INGREDIENTS);
                        return randomDelay(400);
                    }
                    // Combine ingredients: use one on the other
                    IInventoryItem flour = getInventoryFirst(FLOUR_NAMES);
                    IInventoryItem water = getInventoryFirst(WATER_NAMES);
                    if (flour != null && water != null) {
                        log.info("Mixing flour and water to make dough.");
                        flour.useOn(water);
                        return randomDelay(1200);
                    }
                    log.info("Flour or water not found for mixing.");
                } else {
                    setSubState(SubState.BAKE_BREAD);
                    log.info("Bread dough in inventory; switching to BAKE_BREAD.");
                }
                return randomDelay(600);
            }

            case BAKE_BREAD: {
                // If we lose the dough or fail to get bread, fallback to remixing
                if (!hasDough() && !hasBread()) {
                    log.warn("No bread dough and no bread found, possible burn/loss. Switching to GET_INGREDIENTS.");
                    setSubState(SubState.GET_INGREDIENTS);
                    return randomDelay(400);
                }
                // Use dough on range to bake bread
                if (hasDough() && !hasBread()) {
                    IInventoryItem dough = getInventoryFirst(DOUGH_NAMES);
                    ITileObject range = getRange();
                    if (dough != null && range != null) {
                        log.info("Baking bread by using dough on range.");
                        dough.useOn(range);
                        return randomDelay(1800);
                    }
                    log.info("Range or dough missing for baking.");
                } else if (hasBread()) {
                    setSubState(SubState.MOVE_ON); // skip MUSIC_PLAYER for now
                    log.info("Bread detected, proceeding to MOVE_ON.");
                }
                return randomDelay(600);
            }

            case MOVE_ON: {
                log.info("Master Chef complete. Advancing to QUEST_GUIDE step.");
                plugin.setCurrentState(JGTutorialIslandState.QUEST_GUIDE);
                return randomDelay(600);
            }
        }
        return randomDelay(600);
    }

    // ---- Inventory/Widget/NPC null-safe helpers ----
    private boolean hasFlour() { try { return Inventory.contains(FLOUR_NAMES); } catch (Exception e) { return false; } }
    private boolean hasWater() { try { return Inventory.contains(WATER_NAMES); } catch (Exception e) { return false; } }
    private boolean hasDough() { try { return Inventory.contains(DOUGH_NAMES); } catch (Exception e) { return false; } }
    private boolean hasBread() { try { return Inventory.contains(BREAD_NAMES); } catch (Exception e) { return false; } }

    private IInventoryItem getInventoryFirst(String[] names) {
        try { return Inventory.getFirst(names); } catch (Exception e) { return null; }
    }
    private INPC getChef() {
        try { return NPCs.query().ids(MASTER_CHEF_ID).results().nearest(Players.getLocal()); } catch (Exception e) { return null; }
    }
    private boolean isAt(INPC npc) {
        try {
            return npc != null &&
                    Players.getLocal() != null &&
                    Players.getLocal().getWorldLocation() != null &&
                    npc.getWorldLocation() != null &&
                    Players.getLocal().getWorldLocation().distanceTo(npc.getWorldLocation()) <= 2;
        } catch (Exception e) {
            return false;
        }
    }
    private ITileObject getRange() {
        try { return TileObjects.getNearest(RANGE_ID); } catch (Exception e) { return null; }
    }

    // --- Substate inference for safe restarts ---
    private SubState inferSubState() {
        try {
            if (hasBread()) {
                log.info("Infer: Bread in inventory, ready to MOVE_ON.");
                return SubState.MOVE_ON;
            }
            if (hasDough()) {
                log.info("Infer: Bread dough in inventory, ready to bake.");
                return SubState.BAKE_BREAD;
            }
            if (hasFlour() && hasWater()) {
                log.info("Infer: Flour and water present, ready to MIX_DOUGH.");
                return SubState.MIX_DOUGH;
            }
            INPC chef = getChef();
            if (chef == null || !isAt(chef)) {
                log.info("Infer: Need to walk to chef.");
                return SubState.WALK_TO_CHEF;
            }
        } catch (Exception e) {
            log.warn("Exception in inferSubState: {}", e.toString());
        }
        log.info("Infer: Defaulting to TALK_CHEF.");
        return SubState.TALK_CHEF;
    }

    private int randomDelay(int base) {
        return base + (int) (Math.random() * 200) - 100;
    }

    @Override
    public boolean isComplete() {
        boolean complete = plugin.getCurrentState() != JGTutorialIslandState.MASTER_CHEF;
        log.info("isComplete called: currentState={}, complete={}", plugin.getCurrentState(), complete);
        return complete;
    }
}
