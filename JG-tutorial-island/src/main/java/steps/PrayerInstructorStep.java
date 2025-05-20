package steps;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.storm.api.domain.actors.INPC;
import net.storm.plugins.tutorialisland.JGTutorialIslandPlugin;
import net.storm.plugins.tutorialisland.JGTutorialIslandState;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Widgets;
import net.storm.api.domain.widgets.IWidget;

@Slf4j
public class PrayerInstructorStep implements TutorialStep {
    private final JGTutorialIslandPlugin plugin;

    // ---- Constants ----
    private static final int PRAYER_INSTRUCTOR_ID = 3319;
    private static final int CHAPEL_X = 3123, CHAPEL_Y = 3106, PLANE = 0;
    private static final int PRAYER_TAB_PARENT = 164, PRAYER_TAB_CHILD = 57;
    private static final int FRIENDS_TAB_PARENT = 164, FRIENDS_TAB_CHILD = 40;
    private static final int TEXT_BOX_PARENT = 263, TEXT_BOX_CHILD = 1, TEXT_BOX_INDEX = 0;

    private enum SubState {
        WALK_TO_CHAPEL,
        TALK_INSTRUCTOR_1,
        OPEN_PRAYER_TAB,
        TALK_INSTRUCTOR_2,
        OPEN_FRIENDS_TAB,
        FINAL_DIALOGUE,
        MOVE_ON
    }
    @Getter
    private SubState subState;

    public PrayerInstructorStep(JGTutorialIslandPlugin plugin) {
        this.plugin = plugin;
        this.subState = SubState.WALK_TO_CHAPEL;
    }

    private void setSubState(SubState next) {
        if (subState != next) log.info("Changing substate: {} -> {}", subState, next);
        subState = next;
    }

    @Override
    public boolean validate() {
        boolean valid = plugin.getCurrentState() == JGTutorialIslandState.PRAYER_INSTRUCTOR;
        log.info("Validate called: currentState={}, valid={}", plugin.getCurrentState(), valid);
        return valid;
    }

    @Override
    public int execute() {
        plugin.incrementTick();

        // Robust state re-eval: keep running until state stabilizes (no change)
        boolean inferredAndProcessed;
        do {
            inferredAndProcessed = false;
            if (!Dialog.isOpen() && !Players.getLocal().isInteracting()) {
                SubState inferred = inferSubState();
                if (inferred != subState) {
                    setSubState(inferred);
                    inferredAndProcessed = true; // process again!
                }
            }
        } while (inferredAndProcessed);

        log.info("---- PrayerInstructorStep Tick ---- Current subState: {}", subState);

        switch (subState) {
            case WALK_TO_CHAPEL: {
                if (!isAtChapel()) {
                    Movement.walkTo(CHAPEL_X, CHAPEL_Y, PLANE);
                    log.info("Walking to chapel for Prayer Instructor.");
                    return randomDelay(1000);
                }
                setSubState(SubState.TALK_INSTRUCTOR_1);
                return randomDelay(600);
            }

            case TALK_INSTRUCTOR_1: {
                if (handleDialog()) return randomDelay(200);
                if (hasTutorialText("your final instructor")) {
                    setSubState(SubState.MOVE_ON); return randomDelay(400);
                }
                if (hasTutorialText("prayer menu")) {
                    setSubState(SubState.OPEN_PRAYER_TAB); return randomDelay(400);
                }
                if (hasTutorialText("friends and ignore")) {
                    setSubState(SubState.OPEN_FRIENDS_TAB); return randomDelay(400);
                }
                INPC brace = getPrayerInstructor();
                if (brace != null && isAt(brace) && !Players.getLocal().isInteracting()) {
                    brace.interact("Talk-to");
                    log.info("Talking to Prayer Instructor (Brother Brace).");
                    return randomDelay(1200);
                }
                // Fallback: If dialog is closed, try prayer tab
                if (!Dialog.isOpen() && !Dialog.isViewingOptions()) {
                    setSubState(SubState.OPEN_PRAYER_TAB);
                    return randomDelay(600);
                }
                return randomDelay(600);
            }

            case OPEN_PRAYER_TAB: {
                IWidget prayerTab = getWidgetSafe(PRAYER_TAB_PARENT, PRAYER_TAB_CHILD);
                if (prayerTab != null && prayerTab.isVisible()) {
                    prayerTab.click();
                    setSubState(SubState.TALK_INSTRUCTOR_2);
                    return randomDelay(700);
                }
                log.info("Prayer tab not found, waiting...");
                return randomDelay(600);
            }

            case TALK_INSTRUCTOR_2: {
                if (handleDialog()) return randomDelay(200);
                if (hasTutorialText("friends and ignore")) {
                    setSubState(SubState.OPEN_FRIENDS_TAB); return randomDelay(400);
                }
                INPC brace = getPrayerInstructor();
                if (brace != null && isAt(brace) && !Players.getLocal().isInteracting()) {
                    brace.interact("Talk-to");
                    log.info("Talking to Prayer Instructor (after prayer tab).");
                    return randomDelay(1200);
                }
                // Defensive: If dialog is closed and we're stuck, open friends tab
                if (!Dialog.isOpen() && !Dialog.isViewingOptions()) {
                    setSubState(SubState.OPEN_FRIENDS_TAB);
                    return randomDelay(600);
                }
                return randomDelay(600);
            }

            case OPEN_FRIENDS_TAB: {
                IWidget friendsTab = getWidgetSafe(FRIENDS_TAB_PARENT, FRIENDS_TAB_CHILD);
                if (friendsTab != null && friendsTab.isVisible()) {
                    friendsTab.click();
                    setSubState(SubState.FINAL_DIALOGUE);
                    return randomDelay(700);
                }
                log.info("Friends tab not found, waiting...");
                return randomDelay(600);
            }

            case FINAL_DIALOGUE: {
                // Special "ready to move on" dialogue option check
                IWidget moveOnOption = getWidgetSafe(219, 1, 3);
                if (moveOnOption != null && moveOnOption.isVisible() && moveOnOption.getText() != null
                        && moveOnOption.getText().toLowerCase().contains("ready to move on")) {
                    Dialog.chooseOption(3);
                    log.info("Selected special 'ready to move on' dialog option (219,1,3).");
                    setSubState(SubState.MOVE_ON);
                    return randomDelay(400);
                }
                if (handleDialog()) return randomDelay(200);
                if (hasTutorialText("your final instructor")) {
                    setSubState(SubState.MOVE_ON); return randomDelay(400);
                }
                INPC brace = getPrayerInstructor();
                if (brace != null && isAt(brace) && !Players.getLocal().isInteracting()) {
                    brace.interact("Talk-to");
                    log.info("Talking to Prayer Instructor (final wrap-up).");
                    return randomDelay(1200);
                }
                // Defensive: If dialog is closed and we're stuck, move on
                if (!Dialog.isOpen() && !Dialog.isViewingOptions()) {
                    setSubState(SubState.MOVE_ON);
                    return randomDelay(600);
                }
                return randomDelay(600);
            }


            case MOVE_ON: {
                log.info("Completed Prayer Instructor step! Setting plugin state to MAGIC_INSTRUCTOR.");
                plugin.setCurrentState(JGTutorialIslandState.MAGIC_INSTRUCTOR);
                return randomDelay(600);
            }
        }
        return randomDelay(600);
    }

    // --- State inference logic for crash/restart robustness ---
    private SubState inferSubState() {
        INPC brace = getPrayerInstructor();
        String msg = getTutorialText();
        if (brace == null || !isAt(brace)) return SubState.WALK_TO_CHAPEL;
        if (msg.contains("your final instructor")) return SubState.MOVE_ON;
        if (msg.contains("prayer menu")) return SubState.OPEN_PRAYER_TAB;
        if (msg.contains("friends and ignore")) return SubState.OPEN_FRIENDS_TAB;
        if (Dialog.isOpen() || Dialog.isViewingOptions()) {
            if (subState == SubState.TALK_INSTRUCTOR_1 || subState == SubState.TALK_INSTRUCTOR_2 || subState == SubState.FINAL_DIALOGUE)
                return subState;
        }
        if (!isPrayerTabOpen()) return SubState.OPEN_PRAYER_TAB;
        if (!isFriendsTabOpen() && subState != SubState.OPEN_FRIENDS_TAB) return SubState.TALK_INSTRUCTOR_2;
        if (!isFriendsTabOpen()) return SubState.OPEN_FRIENDS_TAB;
        if (!Dialog.isOpen() && !Dialog.isViewingOptions()) return SubState.MOVE_ON;
        return subState;
    }

    // --- Helper/utility methods ---
    private INPC getPrayerInstructor() {
        try { return NPCs.query().ids(PRAYER_INSTRUCTOR_ID).results().nearest(Players.getLocal()); }
        catch (Exception e) { log.warn("getPrayerInstructor exception: {}", e.toString()); return null; }
    }
    private boolean isAtChapel() {
        if (Players.getLocal() == null || Players.getLocal().getWorldLocation() == null)
            return false;
        int px = Players.getLocal().getWorldLocation().getX();
        int py = Players.getLocal().getWorldLocation().getY();
        return Math.abs(px - CHAPEL_X) <= 2 && Math.abs(py - CHAPEL_Y) <= 2;
    }
    private boolean isAt(INPC npc) {
        try {
            return npc != null && Players.getLocal() != null &&
                    Players.getLocal().getWorldLocation() != null && npc.getWorldLocation() != null &&
                    Players.getLocal().getWorldLocation().distanceTo(npc.getWorldLocation()) <= 2;
        } catch (Exception e) { return false; }
    }
    private IWidget getWidgetSafe(int parent, int child) {
        try { return Widgets.get(parent, child); }
        catch (Exception e) { return null; }
    }
    private IWidget getWidgetSafe(int parent, int child, int index) {
        try { return Widgets.get(parent, child, index); }
        catch (Exception e) { return null; }
    }
    // Null-safe and lowercase
    private String getTutorialText() {
        IWidget w = getWidgetSafe(TEXT_BOX_PARENT, TEXT_BOX_CHILD, TEXT_BOX_INDEX);
        return (w != null && w.isVisible() && w.getText() != null) ? w.getText().toLowerCase() : "";
    }
    // Null-safe substring check for tutorial text
    private boolean hasTutorialText(String snippet) {
        return getTutorialText().contains(snippet.toLowerCase());
    }
    private boolean isPrayerTabOpen() {
        IWidget prayerTab = getWidgetSafe(PRAYER_TAB_PARENT, PRAYER_TAB_CHILD);
        return prayerTab != null && prayerTab.isVisible();
    }
    private boolean isFriendsTabOpen() {
        IWidget friendsTab = getWidgetSafe(FRIENDS_TAB_PARENT, FRIENDS_TAB_CHILD);
        return friendsTab != null && friendsTab.isVisible();
    }
    private boolean handleDialog() {
        if (Dialog.isViewingOptions()) {
            Dialog.chooseOption(1); return true;
        }
        if (Dialog.isOpen() && Dialog.canContinue()) {
            Dialog.continueSpace(); return true;
        }
        return false;
    }
    private int randomDelay(int base) {
        return base + (int) (Math.random() * 200) - 100;
    }
    @Override
    public boolean isComplete() {
        boolean complete = plugin.getCurrentState() != JGTutorialIslandState.PRAYER_INSTRUCTOR;
        log.info("isComplete called: currentState={}, complete={}", plugin.getCurrentState(), complete);
        return complete;
    }
}
