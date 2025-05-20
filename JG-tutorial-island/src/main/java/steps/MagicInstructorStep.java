package steps;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.actors.IPlayer;
import net.storm.api.magic.Spell;
import net.storm.api.magic.SpellBook;
import net.storm.plugins.tutorialisland.JGTutorialIslandPlugin;
import net.storm.plugins.tutorialisland.JGTutorialIslandState;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Widgets;
import net.storm.api.domain.widgets.IWidget;

@Slf4j
public class MagicInstructorStep implements TutorialStep {
    private final JGTutorialIslandPlugin plugin;

    // --- Constants (replace widget/coords as needed) ---
    private static final int MAGIC_INSTRUCTOR_ID = 3309;
    private static final int CHICKEN_ID = 3316;
    private static final int AIR_RUNE_ID = 556, MIND_RUNE_ID = 558;
    private static final int INSTRUCTOR_X = 3142, INSTRUCTOR_Y = 3085, PLANE = 0;

    // Widgets (placeholder, replace if different in your client)
    private static final int SPELLBOOK_TAB_PARENT = 164, SPELLBOOK_TAB_CHILD = 58;
    private static final int TEXT_BOX_PARENT = 263, TEXT_BOX_CHILD = 1, TEXT_BOX_INDEX = 0;

    // Spell (using API, no menu manipulation needed)
    private static final Spell WIND_STRIKE = SpellBook.Standard.WIND_STRIKE;

    private enum SubState {
        WALK_TO_INSTRUCTOR,
        TALK_INSTRUCTOR_1,
        OPEN_SPELLBOOK,
        TALK_INSTRUCTOR_2,
        CAST_WIND_STRIKE,
        FINAL_DIALOGUE,
        MOVE_ON
    }
    @Getter
    private SubState subState;

    public MagicInstructorStep(JGTutorialIslandPlugin plugin) {
        this.plugin = plugin;
        this.subState = SubState.WALK_TO_INSTRUCTOR;
    }

    private void setSubState(SubState next) {
        if (subState != next) log.info("Changing substate: {} -> {}", subState, next);
        subState = next;
    }

    @Override
    public boolean validate() {
        boolean valid = plugin.getCurrentState() == JGTutorialIslandState.MAGIC_INSTRUCTOR;
        log.info("Validate called: currentState={}, valid={}", plugin.getCurrentState(), valid);
        return valid;
    }

    @Override
    public int execute() {
        plugin.incrementTick();

        // Re-infer substate for crash/restart safety
        if (!Players.getLocal().isInteracting()) {
            SubState inferred = inferSubState();
            if (inferred != subState) setSubState(inferred);
        }

        log.info("---- MagicInstructorStep Tick ---- Current subState: {}", subState);

        switch (subState) {
            case WALK_TO_INSTRUCTOR: {
                if (!isAtInstructor()) {
                    Movement.walkTo(INSTRUCTOR_X, INSTRUCTOR_Y, PLANE);
                    log.info("Walking to Magic Instructor.");
                    return randomDelay(1000);
                }
                setSubState(SubState.TALK_INSTRUCTOR_1);
                log.info("Arrived at Magic Instructor.");
                return randomDelay(600);
            }
            case TALK_INSTRUCTOR_1: {
                String txt = getWidgetText();
                // Defensive: only move on if dialog is over *and* we have both runes or the right textbox
                boolean dialogDone = !Dialog.isOpen() && !Dialog.isViewingOptions();
                boolean hasRunes = hasRunes();
                boolean shouldAdvance = (txt.contains("final menu") || hasRunes);

                if (shouldAdvance) {
                    setSubState(SubState.OPEN_SPELLBOOK);
                    log.info("Done with dialog + runes received or prompt seen, going to OPEN_SPELLBOOK.");
                    return randomDelay(400);
                }
                if (txt.contains("cast wind strike")) {
                    setSubState(SubState.CAST_WIND_STRIKE);
                    return randomDelay(400);
                }
                if (txt.contains("congratulations, you have completed")) {
                    setSubState(SubState.FINAL_DIALOGUE);
                    return randomDelay(400);
                }
                // Always continue dialog if open
                if (Dialog.isViewingOptions()) {
                    Dialog.chooseOption(1);
                    log.info("Choosing dialog option 1 (Magic Instructor, intro).");
                    return randomDelay(400);
                }
                if (Dialog.isOpen() && Dialog.canContinue()) {
                    Dialog.continueSpace();
                    log.info("Continuing dialog (Magic Instructor, intro).");
                    return randomDelay(200);
                }
                INPC instructor = getInstructor();
                // Only start dialog if not already talking or interacting
                if (instructor != null && isAt(instructor) && !Players.getLocal().isInteracting()) {
                    instructor.interact("Talk-to");
                    log.info("Talking to Magic Instructor.");
                    return randomDelay(1200);
                }
                return randomDelay(600);
            }

            case TALK_INSTRUCTOR_2: {
                String txt = getWidgetText();
                // If the dialog is prompting to cast Wind Strike, move on
                if (txt.contains("you now have some runes")) {
                    setSubState(SubState.CAST_WIND_STRIKE);
                    return randomDelay(400);
                }
                // Always continue dialog if open
                if (Dialog.isViewingOptions()) {
                    Dialog.chooseOption(1);
                    log.info("Choosing dialog option 1 (Magic Instructor, post-spellbook).");
                    return randomDelay(400);
                }
                if (Dialog.isOpen() && Dialog.canContinue()) {
                    Dialog.continueSpace();
                    log.info("Continuing dialog (Magic Instructor, post-spellbook).");
                    return randomDelay(200);
                }
                INPC instructor = getInstructor();
                if (instructor != null && isAt(instructor) && !Players.getLocal().isInteracting()) {
                    instructor.interact("Talk-to");
                    log.info("Talking to Magic Instructor (after spellbook).");
                    return randomDelay(1200);
                }
                // If dialog is closed and text box says to cast, move on
                if (!Dialog.isViewingOptions() && txt.contains("cast wind strike")) {
                    setSubState(SubState.CAST_WIND_STRIKE);
                    log.info("Dialog done and cast prompt detected, moving to CAST_WIND_STRIKE.");
                    return randomDelay(400);
                }
                return randomDelay(600);
            }

            case OPEN_SPELLBOOK: {
                IWidget spellbookTab = getWidgetSafe(SPELLBOOK_TAB_PARENT, SPELLBOOK_TAB_CHILD);
                if (spellbookTab != null && spellbookTab.isVisible()) {
                    spellbookTab.click();
                    log.info("Clicked spellbook tab.");
                    setSubState(SubState.TALK_INSTRUCTOR_2);
                    return randomDelay(700);
                }
                log.info("Spellbook tab not found, waiting...");
                return randomDelay(600);
            }
            case CAST_WIND_STRIKE: {
                if (!WIND_STRIKE.canCast()) {
                    log.warn("Cannot cast Wind Strike (missing runes or interface not ready?)");
                    // Optional: interact with instructor for more runes
                    return randomDelay(800);
                }
                INPC chicken = getNearestChicken();
                if (chicken != null && !chicken.isDead() && !chicken.isInteracting()) {
                    WIND_STRIKE.castOn(chicken);
                    log.info("Casting Wind Strike on chicken.");
                    setSubState(SubState.FINAL_DIALOGUE);
                    return randomDelay(1600);
                }
                log.info("No available chicken found. Waiting...");
                return randomDelay(1000);
            }
            case FINAL_DIALOGUE: {
                INPC adventurerJonh = null;
                try {
                    adventurerJonh = NPCs.query().ids(9244).results().nearest(Players.getLocal());
                } catch (Exception e) {
                }
                if (adventurerJonh != null && adventurerJonh.getWorldLocation() != null) {
                    log.info("Adventurer Jonh detected nearby! ({} {}) Tutorial Island is complete.",
                            adventurerJonh.getWorldLocation().getX(), adventurerJonh.getWorldLocation().getY());
                    setSubState(SubState.MOVE_ON);
                    plugin.setCurrentState(JGTutorialIslandState.COMPLETE);
                    return randomDelay(400);
                }
                if (Dialog.isViewingOptions()) {
                    if (getWidgetSafe(219,1,3).getText().toLowerCase().contains(
                            "i'm not planning to do that"
                    )){
                        Dialog.chooseOption(3);
                    }
                    Dialog.chooseOption(1);
                    log.info("Choosing dialog option 1 (Magic Instructor, final wrap-up).");
                    return randomDelay(400);
                }
                if (Dialog.isOpen() && Dialog.canContinue()) {
                    Dialog.continueSpace();
                    log.info("Continuing dialog (Magic Instructor, final wrap-up).");
                    return randomDelay(200);
                }
                INPC instructor = getInstructor();
                if (instructor != null && isAt(instructor) && !Players.getLocal().isInteracting()) {
                    instructor.interact("Talk-to");
                    log.info("Talking to Magic Instructor (final wrap-up).");
                    return randomDelay(1200);
                }
                String txt = getWidgetText();
                if (txt.contains("congratulations, you have completed")) {
                    setSubState(SubState.MOVE_ON);
                    log.info("Tutorial complete, moving on!");
                    return randomDelay(600);
                }
                if (!Dialog.isOpen() && !Dialog.isViewingOptions()) {
                    setSubState(SubState.MOVE_ON);
                    log.info("Dialog closed, moving on.");
                    return randomDelay(600);
                }
                return randomDelay(600);
            }
            case MOVE_ON: {
                log.info("Completed Magic Instructor step! Setting plugin state to TUTORIAL_ISLAND_DONE.");
                plugin.setCurrentState(JGTutorialIslandState.COMPLETE);
                return randomDelay(600);
            }
        }
        return randomDelay(600);
    }

    // --- State inference logic for crash/restart robustness ---
    private SubState inferSubState() {
        // 1. If Adventurer Jonh (ID 9244) is near, player is in Lumbridge: tutorial is complete
        INPC adventurerJonh = null;
        try {
            adventurerJonh = NPCs.query().ids(9244).results().nearest(Players.getLocal());
        } catch (Exception e) {
            // ignore
        }
        if (adventurerJonh != null && adventurerJonh.getWorldLocation() != null) {
            log.info("inferSubState: Detected Adventurer Jonh nearby. Completing Tutorial Island.");
            return SubState.MOVE_ON;
        }

        // 2. [Optional backup] Location check for Lumbridge (if you want)
        if (Players.getLocal() != null && Players.getLocal().getWorldLocation() != null) {
            int x = Players.getLocal().getWorldLocation().getX();
            int y = Players.getLocal().getWorldLocation().getY();
            int plane = Players.getLocal().getWorldLocation().getPlane();
            if (plane == 0 && Math.abs(x - 3222) <= 3 && Math.abs(y - 3218) <= 3) {
                log.info("inferSubState: Player is at Lumbridge arrival area. Completing Tutorial Island.");
                return SubState.MOVE_ON;
            }
        }
        String txt = getWidgetText();
        boolean dialogDone = !Dialog.isOpen() && !Dialog.isViewingOptions();
        boolean hasRunes = hasRunes();
        // Now check for spellbook just opened: if spellbook is open but NOT cast prompt, go to TALK_INSTRUCTOR_2
        if (isSpellbookOpen() && txt.contains("this is your magic interface") && dialogDone) {
            return SubState.TALK_INSTRUCTOR_2;
        }
        if ((txt.contains("magic menu") || hasRunes) && dialogDone && !isSpellbookOpen()) {
            return SubState.OPEN_SPELLBOOK;
        }
        if (txt.contains("cast wind strike")) {
            return SubState.CAST_WIND_STRIKE;
        }
        if (txt.contains("congratulations, you have completed")) {
            return SubState.MOVE_ON;
        }
        if (!isSpellbookOpen() && dialogDone && hasRunes) {
            return SubState.OPEN_SPELLBOOK;
        }
        return subState;
    }

    // --- Helpers ---
    private INPC getInstructor() {
        try { return NPCs.query().ids(MAGIC_INSTRUCTOR_ID).results().nearest(Players.getLocal()); }
        catch (Exception e) { log.warn("getInstructor exception: {}", e.toString()); return null; }
    }
    private boolean isAtInstructor() {
        if (Players.getLocal() == null || Players.getLocal().getWorldLocation() == null)
            return false;
        int px = Players.getLocal().getWorldLocation().getX();
        int py = Players.getLocal().getWorldLocation().getY();
        return Math.abs(px - INSTRUCTOR_X) <= 7 && Math.abs(py - INSTRUCTOR_Y) <= 7;
    }
    private boolean isAt(INPC npc) {
        try {
            return npc != null && Players.getLocal() != null &&
                    Players.getLocal().getWorldLocation() != null && npc.getWorldLocation() != null &&
                    Players.getLocal().getWorldLocation().distanceTo(npc.getWorldLocation()) <= 2;
        } catch (Exception e) { return false; }
    }
    private INPC getNearestChicken() {
        try { return NPCs.query().ids(CHICKEN_ID).results().nearest(Players.getLocal()); }
        catch (Exception e) { return null; }
    }
    private IWidget getWidgetSafe(int parent, int child) {
        try { return Widgets.get(parent, child); }
        catch (Exception e) { return null; }
    }
    private IWidget getWidgetSafe(int parent, int child, int idx) {
        try { return Widgets.get(parent, child, idx); }
        catch (Exception e) { return null; }
    }
    private boolean isSpellbookOpen() {
        IWidget spellTab = getWidgetSafe(SPELLBOOK_TAB_PARENT, SPELLBOOK_TAB_CHILD);
        return spellTab != null && spellTab.isVisible();
    }
    private String getWidgetText() {
        IWidget w = getWidgetSafe(TEXT_BOX_PARENT, TEXT_BOX_CHILD, TEXT_BOX_INDEX);
        return (w != null && w.isVisible() && w.getText() != null) ? w.getText().toLowerCase() : "";
    }
    private boolean hasRunes() {
        // You could also add inventory checks via Inventory.contains(), or leave as is for just dialog/textbox
        return net.storm.sdk.items.Inventory.contains(AIR_RUNE_ID) && net.storm.sdk.items.Inventory.contains(MIND_RUNE_ID);
    }
    private int randomDelay(int base) {
        return base + (int) (Math.random() * 200) - 100;
    }
    @Override
    public boolean isComplete() {
        boolean complete = plugin.getCurrentState() != JGTutorialIslandState.MAGIC_INSTRUCTOR;
        log.info("isComplete called: currentState={}, complete={}", plugin.getCurrentState(), complete);
        return complete;
    }
}
