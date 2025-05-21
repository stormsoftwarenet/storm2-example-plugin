package steps;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.storm.plugins.tutorialisland.JGTutorialIslandPlugin;
import net.storm.plugins.tutorialisland.JGTutorialIslandState;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Widgets;
import net.storm.sdk.game.Client;
import net.storm.api.domain.actors.INPC;

@Slf4j
public class GielinorGuideStep implements TutorialStep {
    private final JGTutorialIslandPlugin plugin;

    private static final int GIELINOR_GUIDE_ID = 3308;
    private static final int SURVIVAL_EXPERT_ID = 8503;
    private static final int SETTINGS_TAB_PARENT = 164, SETTINGS_TAB_CHILD = 41;
    private static final int MOVEON_WIDGET_PARENT = 263, MOVEON_WIDGET_CHILD1 = 1, MOVEON_WIDGET_CHILD2 = 0;
    private static final int NEAR_DISTANCE = 4;

    private enum SubState {
        TALK_GUIDE_1,
        OPEN_SETTINGS,
        TALK_GUIDE_2,
        MOVE_ON
    }
    @Getter
    private SubState subState;

    public GielinorGuideStep(JGTutorialIslandPlugin plugin) {
        this.plugin = plugin;
        // Always start at a safe default; infer true state after world is loaded
        this.subState = SubState.TALK_GUIDE_2;
    }

    private void setSubState(SubState next) {
        if (subState != next) log.info("Changing substate: {} -> {}", subState, next);
        subState = next;
    }

    @Override
    public boolean validate() {
        boolean valid = plugin.getCurrentState() == JGTutorialIslandState.GIELINOR_GUIDE;
        log.info("Validate called: currentState={}, valid={}", plugin.getCurrentState(), valid);
        return valid;
    }

    @Override
    public int execute() {
        plugin.incrementTick();

        // Only re-infer state when not in dialog or interacting (and world is loaded)
        if (!Dialog.isOpen() && !Players.getLocal().isInteracting()) {
            SubState inferred = inferSubState();
            if (inferred != subState) setSubState(inferred);
        }

        log.info("---- GielinorGuideStep Tick ---- Current subState: {}", subState);

        switch (subState) {
            case TALK_GUIDE_1: {
                if (readyToMoveOn()) {
                    log.info("Guide done, ready to move on.");
                    setSubState(SubState.MOVE_ON);
                }
                // If in dialog, continue as usual
                if (Dialog.isViewingOptions()) {
                    log.info("Dialogue options detected: choosing option 1");
                    Dialog.chooseOption(1);
                    return randomDelay(400);
                }
                if (Dialog.isOpen() && Dialog.canContinue()) {
                    log.info("Continuing dialogue...");
                    Dialog.continueSpace();
                    return randomDelay(200);
                }

                // If the settings widget is visible, transition to open settings!
                var w = Widgets.get(MOVEON_WIDGET_PARENT, MOVEON_WIDGET_CHILD1, MOVEON_WIDGET_CHILD2);
                if (w != null && w.isVisible() && w.getText() != null && w.getText().toLowerCase().contains("spanner icon")) {
                    log.info("Prompt to open settings detected.");
                    setSubState(SubState.OPEN_SETTINGS);
                    return randomDelay(400);
                }

                // If hint arrow is gone and we're not in dialog, WAIT for widget prompt, don't keep talking
                if (dialogueJustFinished()) {
                    log.info("First dialogue done, waiting for open settings prompt.");
                    // Do nothing, just idle until widget prompt appears
                    return randomDelay(600);
                }

                // Only talk if arrow is present and we're not already interacting
                INPC arrowNpc = Client.getHintArrowNpc();
                INPC guide = getGuide();
                if ((arrowNpc != null && arrowNpc.getId() == GIELINOR_GUIDE_ID && !Players.getLocal().isInteracting()) ||
                        (arrowNpc == null && isAt(guide) && !Players.getLocal().isInteracting())) {
                    log.info("Interacting with Gielinor Guide...");
                    if (arrowNpc != null) arrowNpc.interact("Talk-to");
                    else if (guide != null) guide.interact("Talk-to");
                    return randomDelay(1200);
                }
                break;
            }

            case OPEN_SETTINGS: {
                var settingsTab = Widgets.get(SETTINGS_TAB_PARENT, SETTINGS_TAB_CHILD);
                if (settingsTab != null && settingsTab.isVisible()) {
                    log.info("Opening settings tab ({},{})", SETTINGS_TAB_PARENT, SETTINGS_TAB_CHILD);
                    settingsTab.click();
                    if (Dialog.isOpen() && Dialog.canContinue()) {
                        log.info("Continue after opening settings.");
                        Dialog.continueSpace();
                        return randomDelay(200);
                    }
                    setSubState(SubState.TALK_GUIDE_2);
                    return randomDelay(600);
                } else {
                    log.info("Settings tab not found or not visible. Waiting...");
                }
                break;
            }
            case TALK_GUIDE_2: {
                if (Dialog.isViewingOptions()) {
                    log.info("Dialogue options (2nd) detected: choosing option 1");
                    Dialog.chooseOption(1);
                    return randomDelay(400);
                }
                if (Dialog.isOpen() && Dialog.canContinue()) {
                    log.info("Continuing dialogue (2nd)...");
                    Dialog.continueSpace();
                    return randomDelay(200);
                }
                if (readyToMoveOn()) {
                    log.info("Guide done, ready to move on.");
                    setSubState(SubState.MOVE_ON);
                }
                INPC arrowNpc2 = Client.getHintArrowNpc();
                INPC guide = getGuide();
                if ((arrowNpc2 != null && arrowNpc2.getId() == GIELINOR_GUIDE_ID && !Players.getLocal().isInteracting()) ||
                        (arrowNpc2 == null && isAt(guide) && !Players.getLocal().isInteracting())) {
                    log.info("Interacting with Gielinor Guide again...");
                    if (arrowNpc2 != null) arrowNpc2.interact("Talk-to");
                    else if (guide != null) guide.interact("Talk-to");
                    return randomDelay(1200);
                }
                if (readyToMoveOn()) {
                    log.info("Guide done, ready to move on.");
                    setSubState(SubState.MOVE_ON);
                }
                break;
            }
            case MOVE_ON:
                log.info("Set plugin state to SURVIVAL_EXPERT");
                plugin.setCurrentState(JGTutorialIslandState.SURVIVAL_EXPERT);
                break;
        }

        return randomDelay(600);
    }

    private INPC getGuide() {
        try {
            return NPCs.query().ids(GIELINOR_GUIDE_ID).results().nearest(Players.getLocal());
        } catch (Exception e) {
            log.warn("NPC query failed (likely world not ready): {}", e.toString());
            return null;
        }
    }

    private boolean isAt(INPC npc) {
        return npc != null &&
                Players.getLocal() != null &&
                Players.getLocal().getWorldLocation() != null &&
                npc.getWorldLocation() != null &&
                Players.getLocal().getWorldLocation().distanceTo(npc.getWorldLocation()) <= NEAR_DISTANCE;
    }

    private boolean dialogueJustFinished() {
        INPC arrowNpc = Client.getHintArrowNpc();
        if (arrowNpc == null) {
            log.info("No hint arrow present after first guide dialogue.");
            return true;
        }
        return false;
    }

    private boolean readyToMoveOn() {
        var w = Widgets.get(MOVEON_WIDGET_PARENT, MOVEON_WIDGET_CHILD1, MOVEON_WIDGET_CHILD2);
        if (w != null && w.isVisible()) {
            String t = w.getText();
            if (t != null && (t.toLowerCase().contains("moving on") || t.toLowerCase().contains("catch some shrimp"))) {
                log.info("Ready to move on: widget text = {}", t);
                return true;
            }
        }
        INPC arrowNpc = Client.getHintArrowNpc();
        if (arrowNpc != null && arrowNpc.getId() == SURVIVAL_EXPERT_ID) {
            log.info("Ready to move on: hint arrow on Survival Expert.");
            return true;
        }
        INPC guide = getGuide();
        if (guide == null) {
            log.info("No Gielinor Guide found (possibly already moved on).");
            return true;
        }
        if (Players.getLocal() == null || Players.getLocal().getWorldLocation() == null || guide.getWorldLocation() == null) {
            log.warn("Null player or guide world location detected; cannot compute distance. Not moving on.");
            return false;
        }
        int distance = Players.getLocal().getWorldLocation().distanceTo(guide.getWorldLocation());
        if (distance > 10) {
            log.info("Ready to move on: player far from guide.");
            return true;
        }
        return false;
    }

    private SubState inferSubState() {
        var w = Widgets.get(MOVEON_WIDGET_PARENT, MOVEON_WIDGET_CHILD1, MOVEON_WIDGET_CHILD2);
        if (w != null && w.isVisible()) {
            String t = w.getText();
            if (t != null) {
                t = t.toLowerCase();
                if (t.contains("catch some shrimp") || t.contains("moving on") || t.contains("proceed")) {
                    log.info("Infer: Widget says 'move on', setting subState=MOVE_ON");
                    return SubState.MOVE_ON;
                }
                if (t.contains("open your settings") || t.contains("you've been given an item")) {
                    log.info("Infer: Widget says 'open settings' or 'got item', setting subState=OPEN_SETTINGS");
                    return SubState.OPEN_SETTINGS;
                }
            }
        }
        if (readyToMoveOn()) return SubState.MOVE_ON;
        log.info("Infer: Defaulting to TALK_GUIDE_1");
        return SubState.TALK_GUIDE_1;
    }

    private int randomDelay(int base) {
        return base + (int) (Math.random() * 200) - 100;
    }

    @Override
    public boolean isComplete() {
        boolean complete = plugin.getCurrentState() != JGTutorialIslandState.GIELINOR_GUIDE;
        log.info("isComplete called: currentState={}, complete={}", plugin.getCurrentState(), complete);
        return complete;
    }
}
