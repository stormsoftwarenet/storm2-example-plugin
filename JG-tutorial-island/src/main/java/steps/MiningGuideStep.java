package steps;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.plugins.tutorialisland.JGTutorialIslandPlugin;
import net.storm.plugins.tutorialisland.JGTutorialIslandState;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Widgets;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.widgets.IWidget;

@Slf4j
public class MiningGuideStep implements TutorialStep {
    private final JGTutorialIslandPlugin plugin;

    // --- Constants ---
    private static final int INSTRUCTOR_ID = 3311;
    private static final int TIN_ROCK_ID = 10080, COPPER_ROCK_ID = 10079, FURNACE_ID = 10082, ANVIL_ID = 2097;
    private static final int INSTRUCTOR_X = 3081, INSTRUCTOR_Y = 9506, INSTRUCTOR_PLANE = 0;

    // Item IDs
    private static final int PICKAXE_ID = 1265, HAMMER_ID = 2347, TIN_ORE_ID = 438, COPPER_ORE_ID = 436,
            BRONZE_BAR_ID = 2349, BRONZE_DAGGER_ID = 1205;

    private enum SubState {
        WALK_TO_INSTRUCTOR,
        TALK_INSTRUCTOR_1,
        MINE_TIN,
        MINE_COPPER,
        SMELT_BAR,
        TALK_INSTRUCTOR_2,
        SMITH_DAGGER,
        MOVE_ON
    }

    @Getter
    private SubState subState;

    public MiningGuideStep(JGTutorialIslandPlugin plugin) {
        this.plugin = plugin;
        // Do NOT inferSubState here; just pick a safe default
        this.subState = SubState.WALK_TO_INSTRUCTOR;
    }

    private void setSubState(SubState next) {
        if (subState != next) log.info("Changing substate: {} -> {}", subState, next);
        subState = next;
    }

    @Override
    public boolean validate() {
        boolean valid = plugin.getCurrentState() == JGTutorialIslandState.MINING_INSTRUCTOR;
        log.info("Validate called: currentState={}, valid={}", plugin.getCurrentState(), valid);
        return valid;
    }

    @Override
    public int execute() {
        plugin.incrementTick();

        // Only infer at runtime, not in constructor
        if (!Dialog.isOpen() && !Players.getLocal().isInteracting()) {
            SubState inferred = inferSubState();
            if (inferred != subState) setSubState(inferred);
        }

        log.info("---- MiningGuideStep Tick ---- Current subState: {}", subState);

        switch (subState) {
            case WALK_TO_INSTRUCTOR: {
                INPC instructor = getInstructor();
                if (instructor == null) {
                    log.warn("Can't find Mining Instructor, walking to fallback tile ({}, {}, {})", INSTRUCTOR_X, INSTRUCTOR_Y, INSTRUCTOR_PLANE);
                    Movement.walkTo(INSTRUCTOR_X, INSTRUCTOR_Y, INSTRUCTOR_PLANE);
                    return randomDelay(1000);
                }
                if (!isAt(instructor)) {
                    Movement.walkTo(instructor.getWorldLocation());
                    log.info("Walking to Mining Instructor at {}", instructor.getWorldLocation());
                    return randomDelay(1000);
                }
                log.info("Arrived at Mining Instructor. Switching to TALK_INSTRUCTOR_1");
                setSubState(SubState.TALK_INSTRUCTOR_1);
                return randomDelay(600);
            }
            case TALK_INSTRUCTOR_1: {
                if (hasPickaxe()) {
                    setSubState(SubState.MINE_TIN);
                    log.info("Received pickaxe. Proceeding to MINE_TIN.");
                    return randomDelay(600);
                }
                if (Dialog.isViewingOptions()) {
                    Dialog.chooseOption(1);
                    log.info("Choosing dialog option 1 for instructor.");
                    return randomDelay(400);
                }
                if (Dialog.isOpen() && Dialog.canContinue()) {
                    Dialog.continueSpace();
                    log.info("Continuing dialog with instructor.");
                    return randomDelay(200);
                }
                INPC instructor = getInstructor();
                if (instructor != null && !Players.getLocal().isInteracting() && isAt(instructor)) {
                    instructor.interact("Talk-to");
                    log.info("Talking to Mining Instructor for pickaxe.");
                    return randomDelay(1200);
                }
                return randomDelay(600);
            }
            case MINE_TIN: {
                if (hasTinOre()) {
                    setSubState(SubState.MINE_COPPER);
                    log.info("Tin ore acquired. Switching to MINE_COPPER.");
                    return randomDelay(600);
                }
                ITileObject tinRock = getTileObjectSafe(TIN_ROCK_ID);
                if (tinRock != null && tinRock.isInteractable()) {
                    tinRock.interact("Mine");
                    log.info("Mining Tin rock.");
                    return randomDelay(1800);
                }
                log.info("Tin rock not found or not interactable.");
                return randomDelay(600);
            }
            case MINE_COPPER: {
                if (hasCopperOre()) {
                    setSubState(SubState.SMELT_BAR);
                    log.info("Copper ore acquired. Switching to SMELT_BAR.");
                    return randomDelay(600);
                }
                ITileObject copperRock = getTileObjectSafe(COPPER_ROCK_ID);
                if (copperRock != null && copperRock.isInteractable()) {
                    copperRock.interact("Mine");
                    log.info("Mining Copper rock.");
                    return randomDelay(1800);
                }
                log.info("Copper rock not found or not interactable.");
                return randomDelay(600);
            }
            case SMELT_BAR: {
                if (hasBronzeBar()) {
                    setSubState(SubState.TALK_INSTRUCTOR_2);
                    log.info("Bronze bar acquired. Switching to TALK_INSTRUCTOR_2.");
                    return randomDelay(600);
                }
                if (!hasTinOre() || !hasCopperOre()) {
                    setSubState(hasTinOre() ? SubState.MINE_COPPER : SubState.MINE_TIN);
                    log.info("Missing tin/copper ore. Resetting mining substates.");
                    return randomDelay(400);
                }
                ITileObject furnace = getTileObjectSafe(FURNACE_ID);
                if (furnace != null) {
                    IInventoryItem tinOre = Inventory.getFirst(TIN_ORE_ID);
                    if (tinOre != null) {
                        tinOre.useOn(furnace);
                        log.info("Smelting ores at furnace.");
                        return randomDelay(1600);
                    }
                }
                log.info("Furnace not found.");
                return randomDelay(600);
            }
            case TALK_INSTRUCTOR_2: {
                if (hasHammer()) {
                    setSubState(SubState.SMITH_DAGGER);
                    log.info("Hammer acquired (mid-state); advancing to SMITH_DAGGER.");
                    return randomDelay(600);
                }
                INPC instructor = getInstructor();
                if (Dialog.isViewingOptions()) {
                    Dialog.chooseOption(1);
                    log.info("Choosing dialog option 1 for instructor (get hammer).");
                    return randomDelay(400);
                }
                if (Dialog.isOpen() && Dialog.canContinue()) {
                    Dialog.continueSpace();
                    log.info("Continuing dialog with instructor (get hammer).");
                    return randomDelay(200);
                }
                if (instructor != null && !Players.getLocal().isInteracting() && isAt(instructor)) {
                    instructor.interact("Talk-to");
                    log.info("Talking to Mining Instructor for hammer.");
                    return randomDelay(1200);
                }
                if (instructor == null || !isAt(instructor)) {
                    Movement.walkTo(INSTRUCTOR_X, INSTRUCTOR_Y, INSTRUCTOR_PLANE);
                    log.info("Instructor not in range, walking to fallback location.");
                    return randomDelay(1000);
                }
                log.info("stuck, unsure what to do right now- DEBUGGG");
                return randomDelay(600);
            }
            case SMITH_DAGGER: {
                if (hasDagger()) {
                    setSubState(SubState.MOVE_ON);
                    log.info("Bronze dagger acquired. Ready to move on.");
                    return randomDelay(600);
                }
                IWidget daggerWidget = getWidgetSafe(312, 9, 2);
                if (daggerWidget != null && daggerWidget.isVisible()) {
                    daggerWidget.click();
                    log.info("Smithing menu open, clicking dagger widget (312,9,2).");
                    return randomDelay(2000);
                }
                if (!hasBronzeBar()) {
                    setSubState(SubState.SMELT_BAR);
                    log.info("No bronze bar for smithing. Returning to SMELT_BAR.");
                    return randomDelay(600);
                }
                ITileObject anvil = getTileObjectSafe(ANVIL_ID);
                IInventoryItem bronzeBar = Inventory.getFirst(BRONZE_BAR_ID);
                if (anvil != null && bronzeBar != null) {
                    bronzeBar.useOn(anvil);
                    log.info("Smithing bronze dagger at anvil (opening smithing menu).");
                    return randomDelay(1600);
                }
                log.info("Anvil or bronze bar missing for smithing.");
                return randomDelay(600);
            }
            case MOVE_ON: {
                log.info("Completed Mining Instructor step! Setting plugin state to COMBAT_INSTRUCTOR.");
                plugin.setCurrentState(JGTutorialIslandState.COMBAT_INSTRUCTOR);
                return randomDelay(600);
            }
        }
        return randomDelay(600);
    }

    // --------- Safe SubState Inference ---------
    private SubState inferSubState() {
        try {
            IWidget movingOnWidget = getWidgetSafe(263, 1, 0);
            if (movingOnWidget != null && movingOnWidget.isVisible()) {
                String text = movingOnWidget.getText();
                if (text != null && text.toLowerCase().contains("combat instructor")) {
                    log.info("Infer: 'Combat Instructor' text present. Substate = MOVE_ON.");
                    return SubState.MOVE_ON;
                }
            }
            if (hasDagger()) {
                log.info("Infer: Bronze dagger present. Substate = MOVE_ON.");
                return SubState.MOVE_ON;
            }
            if (hasHammer()) {
                if (hasBronzeBar()) {
                    log.info("Infer: Have hammer and bar. Substate = SMITH_DAGGER.");
                    return SubState.SMITH_DAGGER;
                }
                log.info("Infer: Have hammer but no bar. Substate = SMELT_BAR.");
                return SubState.SMELT_BAR;
            }
            if (hasBronzeBar()) {
                log.info("Infer: Bronze bar present, ready to get hammer. Substate = TALK_INSTRUCTOR_2.");
                return SubState.TALK_INSTRUCTOR_2;
            }
            if (hasTinOre() && hasCopperOre()) {
                log.info("Infer: Have both ores. Substate = SMELT_BAR.");
                return SubState.SMELT_BAR;
            }
            INPC instructor = getInstructor();
            boolean atInstructor = instructor != null && isAt(instructor);

            if (hasPickaxe() && !hasHammer() && (!hasTinOre() || !hasCopperOre())) {
                if (Dialog.isOpen() || Dialog.isViewingOptions() || (atInstructor && !Players.getLocal().isInteracting())) {
                    log.info("Infer: Have pickaxe but not hammer/ores; talking to instructor for proper progression.");
                    return SubState.TALK_INSTRUCTOR_1;
                }
            }
            if (hasPickaxe() && !hasTinOre()) {
                log.info("Infer: Pickaxe present, not in dialog, ready to mine tin. Substate = MINE_TIN.");
                return SubState.MINE_TIN;
            }
            if (hasPickaxe() && !hasCopperOre()) {
                log.info("Infer: Pickaxe present, not in dialog, ready to mine copper. Substate = MINE_COPPER.");
                return SubState.MINE_COPPER;
            }
            if (instructor == null || !atInstructor) {
                log.info("Infer: Not at instructor, need to WALK_TO_INSTRUCTOR.");
                return SubState.WALK_TO_INSTRUCTOR;
            }
        } catch (Exception e) {
            log.warn("Exception in inferSubState: {}", e.toString());
        }
        log.info("Infer: At instructor, ready to TALK_INSTRUCTOR_1.");
        return SubState.TALK_INSTRUCTOR_1;
    }

    // --------- Utility Methods ---------
    private INPC getInstructor() {
        try {
            return NPCs.query().ids(INSTRUCTOR_ID).results().nearest(Players.getLocal());
        } catch (Exception e) {
            log.warn("getInstructor() exception: {}", e.toString());
            return null;
        }
    }
    private boolean isAt(INPC npc) {
        try {
            return npc != null && Players.getLocal() != null &&
                    Players.getLocal().getWorldLocation() != null && npc.getWorldLocation() != null &&
                    Players.getLocal().getWorldLocation().distanceTo(npc.getWorldLocation()) <= 2;
        } catch (Exception e) {
            return false;
        }
    }
    private ITileObject getTileObjectSafe(int id) {
        try {
            return TileObjects.getNearest(id);
        } catch (Exception e) {
            return null;
        }
    }
    private IWidget getWidgetSafe(int parent, int child) {
        try {
            return Widgets.get(parent, child);
        } catch (Exception e) {
            return null;
        }
    }
    private IWidget getWidgetSafe(int parent, int child, int subchild) {
        try {
            return Widgets.get(parent, child, subchild);
        } catch (Exception e) {
            return null;
        }
    }
    private boolean hasPickaxe()    { return Inventory.contains(PICKAXE_ID); }
    private boolean hasHammer()     { return Inventory.contains(HAMMER_ID); }
    private boolean hasTinOre()     { return Inventory.contains(TIN_ORE_ID); }
    private boolean hasCopperOre()  { return Inventory.contains(COPPER_ORE_ID); }
    private boolean hasBronzeBar()  { return Inventory.contains(BRONZE_BAR_ID); }
    private boolean hasDagger()     { return Inventory.contains(BRONZE_DAGGER_ID); }

    private int randomDelay(int base) {
        return base + (int) (Math.random() * 200) - 100;
    }

    @Override
    public boolean isComplete() {
        boolean complete = plugin.getCurrentState() != JGTutorialIslandState.MINING_INSTRUCTOR;
        log.info("isComplete called: currentState={}, complete={}", plugin.getCurrentState(), complete);
        return complete;
    }
}
