package steps;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.storm.api.domain.actors.INPC;
import net.storm.plugins.tutorialisland.JGTutorialIslandPlugin;
import net.storm.plugins.tutorialisland.JGTutorialIslandState;
import net.storm.sdk.game.Combat;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Widgets;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.domain.tiles.ITileObject;

/**
 * Handles all substeps for the Combat Instructor segment of Tutorial Island.
 * Strongly debounced, safe to run at any point, and immune to most state/step bugs.
 */
@Slf4j
public class CombatInstructorStep implements TutorialStep {
    private final JGTutorialIslandPlugin plugin;

    // --- Game constants ---
    private static final int INSTRUCTOR_ID = 3307;
    private static final int GIANT_RAT_ID = 3313;
    private static final int DAGGER_ID = 1205, SWORD_ID = 1277, SHIELD_ID = 1171, BOW_ID = 841, ARROW_ID = 882;
    private static final int INSTRUCTOR_X = 3105, INSTRUCTOR_Y = 9507, INSTRUCTOR_PLANE = 0;

    private static final int EQUIPMENT_TAB_PARENT = 164, EQUIPMENT_TAB_CHILD = 63;
    private static final int EQUIP_STATS_TAB_PARENT = 387, EQUIP_STATS_TAB_CHILD = 1;
    private static final int COMBAT_STYLES_WIDGET_PARENT = 164, COMBAT_STYLES_WIDGET_CHILD = 52;

    private static final int RAT_AREA_X = 3104;
    private static final int RAT_AREA_Y = 9518;
    private static final int RAT_AREA_VAR = 1; // ±1 tile for human-like movement
    private static final int RAT_DOOR_ID = 9720;

    private enum SubState {
        WALK_TO_INSTRUCTOR, TALK_INSTRUCTOR_1, OPEN_EQUIPMENT, OPEN_EQUIP_STATS, EQUIP_DAGGER,
        TALK_INSTRUCTOR_2, OPEN_EQUIPMENT_2, EQUIP_SWORD_SHIELD, OPEN_COMBAT_STYLES,
        WALK_TO_RAT, KILL_MELEE_RAT, TALK_INSTRUCTOR_3, EQUIP_BOW_ARROWS,
        KILL_RANGE_RAT, FINAL_DIALOGUE, MOVE_ON
    }
    @Getter
    private SubState subState;

    public CombatInstructorStep(JGTutorialIslandPlugin plugin) {
        this.plugin = plugin;
        this.subState = SubState.WALK_TO_INSTRUCTOR;
    }

    private void setSubState(SubState next) {
        if (subState != next) log.info("Changing substate: {} -> {}", subState, next);
        subState = next;
    }

    @Override
    public boolean validate() {
        boolean valid = plugin.getCurrentState() == JGTutorialIslandState.COMBAT_INSTRUCTOR;
        log.info("Validate called: currentState={}, valid={}", plugin.getCurrentState(), valid);
        return valid;
    }

    @Override
    public int execute() {
        plugin.incrementTick();

        // Infer and process substate (no return on substate change)
        boolean inferredAndProcessed;
        do {
            inferredAndProcessed = false;
            if (!Dialog.isOpen() && !Players.getLocal().isInteracting()) {
                SubState inferred = inferSubState();
                if (inferred != subState) {
                    setSubState(inferred);
                    inferredAndProcessed = true; // Reprocess below
                }
            }
        } while (inferredAndProcessed);

        log.info("---- CombatInstructorStep Tick ---- Current subState: {}", subState);

        switch (subState) {
            case WALK_TO_INSTRUCTOR: {
                INPC instructor = getInstructorSafe();
                if (instructor == null || !isAt(instructor)) {
                    log.info("Walking to Combat Instructor at {},{},{}", INSTRUCTOR_X, INSTRUCTOR_Y, INSTRUCTOR_PLANE);
                    Movement.walkTo(INSTRUCTOR_X, INSTRUCTOR_Y, INSTRUCTOR_PLANE);
                    return randomDelay(1000);
                }
                if (hasKilledMeleeRat()) {
                    setSubState(SubState.TALK_INSTRUCTOR_3);
                    return 0;
                }
                setSubState(SubState.TALK_INSTRUCTOR_1);
                return randomDelay(600);
            }

            case TALK_INSTRUCTOR_1: {
                if (handleDialog()) return randomDelay(200);

                if (hasWidgetText("equipping items")) {
                    setSubState(SubState.OPEN_EQUIPMENT);
                    return 0;
                }
                if (hasSwordAnywhere() && hasShieldAnywhere()) {
                    setSubState(SubState.OPEN_EQUIPMENT_2);
                    return randomDelay(600);
                }
                INPC instructor = getInstructorSafe();
                if (instructor != null && !Players.getLocal().isInteracting() && isAt(instructor)
                        && !hasWidgetText("equipping items")) {
                    instructor.interact("Talk-to");
                    return randomDelay(1200);
                }
                return randomDelay(600);
            }

            case OPEN_EQUIPMENT: {
                IWidget equipTab = getWidgetSafe(EQUIPMENT_TAB_PARENT, EQUIPMENT_TAB_CHILD);
                if (equipTab != null && equipTab.isVisible()) {
                    equipTab.click();
                    setSubState(SubState.OPEN_EQUIP_STATS);
                    return randomDelay(600);
                }
                return randomDelay(600);
            }
            case OPEN_EQUIP_STATS: {
                IWidget statsTab = getWidgetSafe(EQUIP_STATS_TAB_PARENT, EQUIP_STATS_TAB_CHILD);
                if (statsTab != null && statsTab.isVisible()) {
                    statsTab.click();
                    setSubState(SubState.EQUIP_DAGGER);
                    return randomDelay(600);
                }
                return randomDelay(600);
            }
            case EQUIP_DAGGER: {
                if (!isAt(getInstructorSafe())) {
                    setSubState(SubState.WALK_TO_INSTRUCTOR);
                    return randomDelay(600);
                }
                if (isBlockedByMiningWidget()) {
                    setSubState(SubState.TALK_INSTRUCTOR_1);
                    return randomDelay(600);
                }
                if (Equipment.contains(DAGGER_ID)) {
                    setSubState(SubState.TALK_INSTRUCTOR_2);
                    return randomDelay(600);
                }
                IInventoryItem dagger = Inventory.getFirst(DAGGER_ID);
                if (dagger != null) {
                    dagger.interact("Wield");
                    return randomDelay(800);
                }
                setSubState(SubState.TALK_INSTRUCTOR_1);
                return randomDelay(600);
            }

            case TALK_INSTRUCTOR_2: {
                if (handleDialog()) return randomDelay(200);

                if (hasSwordAnywhere() && hasShieldAnywhere()) {
                    setSubState(SubState.OPEN_EQUIPMENT_2);
                    return randomDelay(600);
                }
                INPC instructor = getInstructorSafe();
                if (instructor != null && !Players.getLocal().isInteracting() && isAt(instructor)) {
                    instructor.interact("Talk-to");
                    return randomDelay(1200);
                }
                return randomDelay(600);
            }

            case OPEN_EQUIPMENT_2: {
                IWidget equipTab = getWidgetSafe(EQUIPMENT_TAB_PARENT, EQUIPMENT_TAB_CHILD);
                if (equipTab != null && equipTab.isVisible()) {
                    equipTab.click();
                    setSubState(SubState.EQUIP_SWORD_SHIELD);
                    return randomDelay(600);
                }
                return randomDelay(600);
            }
            case EQUIP_SWORD_SHIELD: {
                if (Equipment.contains(SWORD_ID) && Equipment.contains(SHIELD_ID)) {
                    setSubState(SubState.OPEN_COMBAT_STYLES);
                    return randomDelay(600);
                }
                IInventoryItem sword = Inventory.getFirst(SWORD_ID);
                IInventoryItem shield = Inventory.getFirst(SHIELD_ID);
                if (sword != null && !Equipment.contains(SWORD_ID)) {
                    sword.interact("Wield");
                    return randomDelay(600);
                }
                if (shield != null && !Equipment.contains(SHIELD_ID)) {
                    shield.interact("Wield");
                    return randomDelay(600);
                }
                return randomDelay(600);
            }

            case OPEN_COMBAT_STYLES: {
                if (hasKilledMeleeRat()) {
                    setSubState(SubState.TALK_INSTRUCTOR_3);
                    return randomDelay(600);
                }
                IWidget combatTab = getWidgetSafe(COMBAT_STYLES_WIDGET_PARENT, COMBAT_STYLES_WIDGET_CHILD);
                if (combatTab != null && combatTab.isVisible()) {
                    combatTab.click();
                    setSubState(SubState.WALK_TO_RAT);
                    return randomDelay(700);
                }
                return randomDelay(600);
            }

            case WALK_TO_RAT: {
                if (hasKilledRangeRat()) {
                    setSubState(SubState.MOVE_ON);
                    return randomDelay(600);
                }
                if (isDoorBlockingRatArea()) {
                    ITileObject door = getNearestDoor();
                    if (door != null && door.isInteractable()) {
                        door.interact("Open");
                        return randomDelay(1000);
                    }
                }
                int x = RAT_AREA_X + (int) (Math.random() * (RAT_AREA_VAR * 2 + 1)) - RAT_AREA_VAR;
                int y = RAT_AREA_Y + (int) (Math.random() * (RAT_AREA_VAR * 2 + 1)) - RAT_AREA_VAR;
                Movement.walkTo(x, y, INSTRUCTOR_PLANE);
                setSubState(SubState.KILL_MELEE_RAT);
                return randomDelay(1000);
            }

            case KILL_MELEE_RAT: {
                if (hasWidgetText("i can't reach that")) {
                    setSubState(SubState.WALK_TO_RAT);
                    return randomDelay(1000);
                }
                if (hasKilledMeleeRat()) {
                    setSubState(SubState.TALK_INSTRUCTOR_3);
                    return randomDelay(600);
                }
                if (isPlayerInCombat()) {
                    return randomDelay(1000);
                }
                INPC rat = getRatSafe();
                if (rat != null && !rat.isDead() && !rat.isInteracting()) {
                    rat.interact("Attack");
                    return randomDelay(1800);
                }
                return randomDelay(1000);
            }

            case TALK_INSTRUCTOR_3: {
                if (handleDialog()) return randomDelay(200);

                if (hasBow() && hasArrows()) {
                    setSubState(SubState.EQUIP_BOW_ARROWS);
                    return randomDelay(600);
                }
                INPC instructor = getInstructorSafe();
                if (instructor != null && !Players.getLocal().isInteracting() && isAt(instructor)) {
                    instructor.interact("Talk-to");
                    return randomDelay(1200);
                }
                setSubState(SubState.WALK_TO_INSTRUCTOR); // Safety
                return randomDelay(600);
            }

            case EQUIP_BOW_ARROWS: {
                if (Equipment.contains(BOW_ID) && Equipment.contains(ARROW_ID)) {
                    setSubState(SubState.KILL_RANGE_RAT);
                    return randomDelay(600);
                }
                IInventoryItem bow = Inventory.getFirst(BOW_ID);
                IInventoryItem arrows = Inventory.getFirst(ARROW_ID);
                if (bow != null && !Equipment.contains(BOW_ID)) {
                    bow.interact("Wield");
                    return randomDelay(800);
                }
                if (arrows != null && !Equipment.contains(ARROW_ID)) {
                    arrows.interact("Wield");
                    return randomDelay(800);
                }
                return randomDelay(600);
            }
            case KILL_RANGE_RAT: {
                if (hasKilledRangeRat()) {
                    setSubState(SubState.MOVE_ON);
                    return randomDelay(600);
                }
                if (hasWidgetText("i can't reach that")) {
                    Movement.walkTo(3106, 9510);
                    return randomDelay(600);
                }
                INPC rat = getRatSafe();
                if (rat != null && !rat.isDead() && !rat.isInteracting()) {
                    rat.interact("Attack");
                    return randomDelay(1800);
                }
                return randomDelay(800);
            }
            case FINAL_DIALOGUE: {
                if (handleDialog()) return randomDelay(200);

                INPC instructor = getInstructorSafe();
                if (instructor != null && !Players.getLocal().isInteracting() && isAt(instructor)) {
                    instructor.interact("Talk-to");
                    return randomDelay(1200);
                }
                IWidget moveOnWidget = getWidgetSafe(263, 1, 0);
                if (moveOnWidget != null && moveOnWidget.isVisible()) {
                    setSubState(SubState.MOVE_ON);
                    return randomDelay(600);
                }
                return randomDelay(600);
            }
            case MOVE_ON: {
                plugin.setCurrentState(JGTutorialIslandState.BANKER);
                return randomDelay(600);
            }
        }
        return randomDelay(600);
    }

    // --- State Inference Logic ---
    private SubState inferSubState() {
        INPC instructor = getInstructorSafe();
        if (instructor == null || !isAt(instructor)) return SubState.WALK_TO_INSTRUCTOR;
        IWidget moveOnWidget = getWidgetSafe(263, 1, 0);
        String moveOnText = (moveOnWidget != null && moveOnWidget.isVisible()) ? moveOnWidget.getText() : null;
        if (isBlockedByMiningWidget()) return SubState.TALK_INSTRUCTOR_1;
        if (moveOnText != null && moveOnText.toLowerCase().contains("moving on")) return SubState.MOVE_ON;
        if (Equipment.contains(BOW_ID) && Equipment.contains(ARROW_ID) && !hasKilledRangeRat())
            return SubState.KILL_RANGE_RAT;
        if (hasBow() && hasArrows() && (!Equipment.contains(BOW_ID) || !Equipment.contains(ARROW_ID)))
            return SubState.EQUIP_BOW_ARROWS;
        if (hasKilledRangeRat()) return SubState.FINAL_DIALOGUE;
        if (Equipment.contains(SWORD_ID) && Equipment.contains(SHIELD_ID) && !hasKilledMeleeRat())
            return SubState.OPEN_COMBAT_STYLES;
        if (hasSword() && hasShield() && (!Equipment.contains(SWORD_ID) || !Equipment.contains(SHIELD_ID)))
            return SubState.EQUIP_SWORD_SHIELD;
        if (hasKilledMeleeRat()) return SubState.TALK_INSTRUCTOR_3;
        if (Equipment.contains(DAGGER_ID)) return SubState.TALK_INSTRUCTOR_2;
        if (Inventory.contains(DAGGER_ID) && !Equipment.contains(DAGGER_ID)) return SubState.OPEN_EQUIPMENT;
        return SubState.WALK_TO_INSTRUCTOR;
    }

    // --- Helpers ---
    /** Handles dialog continue/choose for all NPC steps */
    private boolean handleDialog() {
        if (Dialog.isViewingOptions()) {
            Dialog.chooseOption(1); return true;
        }
        if (Dialog.isOpen() && Dialog.canContinue()) {
            Dialog.continueSpace(); return true;
        }
        return false;
    }
    /** Null-safe widget text substring check */
    private boolean hasWidgetText(String snippet) {
        IWidget w = getWidgetSafe(263, 1, 0);
        return w != null && w.isVisible() && w.getText() != null && w.getText().toLowerCase().contains(snippet.toLowerCase());
    }

    private boolean isPlayerInCombat() { return Players.getLocal() != null && Players.getLocal().isInteracting(); }
    private boolean hasSwordAnywhere() { return Inventory.contains(SWORD_ID) || Equipment.contains(SWORD_ID); }
    private boolean hasShieldAnywhere() { return Inventory.contains(SHIELD_ID) || Equipment.contains(SHIELD_ID); }
    private INPC getInstructorSafe() {
        try { return NPCs.query().ids(INSTRUCTOR_ID).results().nearest(Players.getLocal()); }
        catch (Exception e) { log.warn("getInstructorSafe exception: {}", e.toString()); return null; }
    }
    private boolean isAt(INPC npc) {
        try {
            return npc != null && Players.getLocal() != null &&
                    Players.getLocal().getWorldLocation() != null && npc.getWorldLocation() != null &&
                    Players.getLocal().getWorldLocation().distanceTo(npc.getWorldLocation()) <= 2;
        } catch (Exception e) { return false; }
    }
    private INPC getRatSafe() {
        try { return Combat.getAttackableNPC(GIANT_RAT_ID); }
        catch (Exception e) { return null; }
    }
    private IWidget getWidgetSafe(int parent, int child) {
        try { return Widgets.get(parent, child); }
        catch (Exception e) { return null; }
    }
    private IWidget getWidgetSafe(int parent, int child, int array) {
        try { return Widgets.get(parent, child, array); }
        catch (Exception e) { return null; }
    }
    private boolean hasSword()  { return Inventory.contains(SWORD_ID); }
    private boolean hasShield() { return Inventory.contains(SHIELD_ID); }
    private boolean hasBow()    { return Inventory.contains(BOW_ID); }
    private boolean hasArrows() { return Inventory.contains(ARROW_ID); }
    private boolean hasKilledMeleeRat() {
        return hasWidgetText("well done, you've made your first kill");
    }
    private boolean hasKilledRangeRat() {
        return hasWidgetText("you have completed the tasks here");
    }
    private boolean isBlockedByMiningWidget() {
        IWidget moveOnWidget = getWidgetSafe(263, 1);
        String moveOnText = (moveOnWidget != null && moveOnWidget.isVisible()) ? moveOnWidget.getText() : null;
        return moveOnText != null && moveOnText.toLowerCase().contains("congratulations, you've made your first weapon");
    }
    private boolean isDoorBlockingRatArea() {
        ITileObject door = getNearestDoor();
        return door != null && door.isInteractable();
    }
    private ITileObject getNearestDoor() {
        try { return net.storm.sdk.entities.TileObjects.getNearest(RAT_DOOR_ID); }
        catch (Exception e) { return null; }
    }
    private int randomDelay(int base) {
        return base + (int) (Math.random() * 200) - 100;
    }
    @Override
    public boolean isComplete() {
        boolean complete = plugin.getCurrentState() != JGTutorialIslandState.COMBAT_INSTRUCTOR;
        log.info("isComplete called: currentState={}, complete={}", plugin.getCurrentState(), complete);
        return complete;
    }
}
