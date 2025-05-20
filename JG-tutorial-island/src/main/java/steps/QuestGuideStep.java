package steps;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.domain.widgets.IWidget;
import net.storm.plugins.tutorialisland.JGTutorialIslandPlugin;
import net.storm.plugins.tutorialisland.JGTutorialIslandState;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Widgets;

@Slf4j
public class QuestGuideStep implements TutorialStep {
    private final JGTutorialIslandPlugin plugin;

    private static final int QUEST_GUIDE_ID = 3312; // Confirm with Dev Tools
    private static final int DOOR_ID = 9721; // Confirm
    private static final int LADDER_ID = 9726; // Confirm
    private static final int QUEST_TAB_BUTTON_PARENT = 164, QUEST_TAB_BUTTON_CHILD = 54;
    private static final int QUEST_TAB_PARENT = 399, QUEST_TAB_CHILD = 7;
    private static final int MUSIC_WIDGET_PARENT = 261, MUSIC_WIDGET_CHILD = 1;
    private static final int QUEST_GUIDE_TILE_X = 3088, QUEST_GUIDE_TILE_Y = 3124;

    private enum SubState {
        WALK_TO_GUIDE,
        OPEN_DOOR,
        TALK_GUIDE_1,
        OPEN_QUESTS_TAB,
        TALK_GUIDE_2,
        MUSIC_PLAYER,
        MOVE_ON
    }

    @Getter
    private SubState subState;

    public QuestGuideStep(JGTutorialIslandPlugin plugin) {
        this.plugin = plugin;
        // Default to TALK_GUIDE_1 (do NOT call inferSubState here)
        this.subState = SubState.WALK_TO_GUIDE;
    }

    private void setSubState(SubState next) {
        if (subState != next) log.info("Changing substate: {} -> {}", subState, next);
        subState = next;
    }

    private boolean openQuestGuideDoorIfNecessary() {
        try {
            ITileObject door = TileObjects.getNearest(DOOR_ID);
            if (door != null && door.isInteractable()) {
                door.interact("Open");
                log.info("Opening Quest Guide room door...");
                return true;
            }
        } catch (Exception e) {
            log.warn("Error while trying to open quest guide door: {}", e.toString());
        }
        return false;
    }

    private boolean goDownLadderIfNecessary() {
        try {
            ITileObject ladder = TileObjects.getNearest(LADDER_ID);
            if (ladder != null && ladder.isInteractable()) {
                ladder.interact("Climb-down");
                log.info("Climbing down ladder to caves...");
                return true;
            }
        } catch (Exception e) {
            log.warn("Error while trying to go down ladder: {}", e.toString());
        }
        return false;
    }

    private INPC getGuide() {
        try {
            return NPCs.query().ids(QUEST_GUIDE_ID).results().nearest(Players.getLocal());
        } catch (Exception e) {
            log.warn("getGuide() exception: {}", e.toString());
            return null;
        }
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

    private boolean isInMiningCave() {
        try {
            return Players.getLocal() != null &&
                    Players.getLocal().getWorldLocation().getY() > 9000;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean validate() {
        if (isInMiningCave()) {
            log.warn("QuestGuideStep: Player is in mining cave; step should be skipped/completed.");
            setSubState(SubState.MOVE_ON);
        }
        boolean valid = plugin.getCurrentState() == JGTutorialIslandState.QUEST_GUIDE;
        log.info("Validate called: currentState={}, valid={}", plugin.getCurrentState(), valid);
        return valid;
    }

    @Override
    public int execute() {
        if (isInMiningCave()) {
            log.warn("QuestGuideStep: Player is in mining cave; skipping step.");
            plugin.setCurrentState(JGTutorialIslandState.MINING_INSTRUCTOR);
            return 0;
        }
        plugin.incrementTick();

        // Safe auto-infer substate at runtime, never in constructor
        if (!Dialog.isOpen() && !Players.getLocal().isInteracting()) {
            SubState inferred = inferSubState();
            if (inferred != subState) setSubState(inferred);
        }

        log.info("---- QuestGuideStep Tick ---- Current subState: {}", subState);

        switch (subState) {
            case WALK_TO_GUIDE: {
                if (openQuestGuideDoorIfNecessary()) return randomDelay(1200);
                INPC guide = getGuide();
                if (guide == null) {
                    log.warn("Can't find Quest Guide NPC");
                    Movement.walkTo(QUEST_GUIDE_TILE_X, QUEST_GUIDE_TILE_Y);
                    return randomDelay(1000);
                }
                if (!isAt(guide)) {
                    Movement.walkTo(guide.getWorldLocation());
                    log.info("Walking to Quest Guide at {}", guide.getWorldLocation());
                    return randomDelay(1000);
                }
                log.info("Arrived at Quest Guide. Switching to TALK_GUIDE_1");
                setSubState(SubState.TALK_GUIDE_1);
                return randomDelay(600);
            }
            case TALK_GUIDE_1: {
                INPC guide = getGuide();
                if (Dialog.isViewingOptions()) {
                    Dialog.chooseOption(1);
                    log.info("Choosing dialog option 1 for guide.");
                    return randomDelay(400);
                }
                if (Dialog.isOpen() && Dialog.canContinue()) {
                    Dialog.continueSpace();
                    log.info("Continuing dialog with guide.");
                    return randomDelay(200);
                }
                var questTabButton = getWidgetSafe(QUEST_TAB_BUTTON_PARENT, QUEST_TAB_BUTTON_CHILD);
                if (questTabButton != null && questTabButton.isVisible()) {
                    setSubState(SubState.OPEN_QUESTS_TAB);
                    log.info("Prompt to open quest journal tab detected, switching to OPEN_QUESTS_TAB.");
                    return randomDelay(600);
                }
                if (guide != null && isAt(guide)) {
                    guide.interact("Talk-to");
                    log.info("Talking to Quest Guide.");
                    return randomDelay(1200);
                }
                return randomDelay(600);
            }
            case OPEN_QUESTS_TAB: {
                var questTabButton = getWidgetSafe(QUEST_TAB_BUTTON_PARENT, QUEST_TAB_BUTTON_CHILD);
                if (questTabButton != null && questTabButton.isVisible()) {
                    questTabButton.click();
                    log.info("Clicked quest journal tab button.");
                    setSubState(SubState.TALK_GUIDE_2);
                    return randomDelay(600);
                }
                log.info("Quest tab button not visible, waiting...");
                return randomDelay(600);
            }
            case TALK_GUIDE_2: {
                var textWidget = getWidgetSafe(263, 1, 0);
                if (textWidget != null && textWidget.isVisible()) {
                    String text = textWidget.getText();
                    if (text != null && text.toLowerCase().contains("mining and smithing") || text.toLowerCase().contains("moving on")) {
                        log.info("Detected 'Mining and Smithing' in TALK_GUIDE_2; ready to move on.");
                        setSubState(SubState.MOVE_ON);
                        return randomDelay(600);
                    }
                }
                INPC guide = getGuide();
                if (Dialog.isViewingOptions()) {
                    Dialog.chooseOption(1);
                    log.info("Dialog options open, picking option 1 (TALK_GUIDE_2).");
                    return randomDelay(400);
                }
                if (Dialog.isOpen() && Dialog.canContinue()) {
                    Dialog.continueSpace();
                    log.info("Dialog open, continuing (TALK_GUIDE_2).");
                    return randomDelay(200);
                }
                var questTab = getWidgetSafe(QUEST_TAB_PARENT, QUEST_TAB_CHILD);
                if ((questTab != null && questTab.isVisible()) && guide != null && isAt(guide)) {
                    guide.interact("Talk-to");
                    log.info("Talking to Quest Guide (2nd dialog).");
                    return randomDelay(1200);
                }
                log.info("Waiting for dialog/journal completion before moving on.");
                return randomDelay(600);
            }
            case MUSIC_PLAYER: {
                var musicWidget = getWidgetSafe(MUSIC_WIDGET_PARENT, MUSIC_WIDGET_CHILD);
                if (musicWidget != null && musicWidget.isVisible()) {
                    musicWidget.interact();
                    log.info("Interacting with music player widget.");
                    setSubState(SubState.MOVE_ON);
                    return randomDelay(1000);
                }
                log.info("MUSIC_PLAYER step - widget not visible or not required, skipping.");
                setSubState(SubState.MOVE_ON);
                return randomDelay(1000);
            }
            case MOVE_ON: {
                if (goDownLadderIfNecessary() || isInMiningCave()) return randomDelay(1200);
                log.info("Completed Quest Guide step! Setting plugin state to next step.");
                plugin.setCurrentState(JGTutorialIslandState.MINING_INSTRUCTOR);
                return randomDelay(600);
            }
        }
        return randomDelay(600);
    }

    private IWidget getWidgetSafe(int parent, int child) {
        try { return Widgets.get(parent, child); } catch (Exception e) { return null; }
    }
    private IWidget getWidgetSafe(int parent, int child, int subchild) {
        try { return Widgets.get(parent, child, subchild); } catch (Exception e) { return null; }
    }

    private int randomDelay(int base) {
        return base + (int) (Math.random() * 200) - 100;
    }

    // Safe runtime substate inference
    private SubState inferSubState() {
        try {
            INPC guide = getGuide();
            if (guide == null || !isAt(guide)) {
                log.info("Infer: Not at Quest Guide, need to WALK_TO_GUIDE.");
                return SubState.WALK_TO_GUIDE;
            }
            var miningWidget = getWidgetSafe(263, 1, 0);
            if (miningWidget != null && miningWidget.isVisible()) {
                String text = miningWidget.getText();
                if (text != null && text.toLowerCase().contains("mining and smithing")) {
                    log.info("Infer: 'Mining and Smithing' text present. Substate = MOVE_ON.");
                    return SubState.MOVE_ON;
                }
            }
            var musicWidget = getWidgetSafe(MUSIC_WIDGET_PARENT, MUSIC_WIDGET_CHILD);
            if (musicWidget != null && musicWidget.isVisible()) {
                log.info("Infer: Music player widget detected, switching to MUSIC_PLAYER.");
                return SubState.MUSIC_PLAYER;
            }
            if (Dialog.isOpen() || Dialog.isViewingOptions()) {
                log.info("Infer: Dialog open or options present, substate = TALK_GUIDE_2.");
                return SubState.TALK_GUIDE_2;
            }
            var questTab = getWidgetSafe(QUEST_TAB_PARENT, QUEST_TAB_CHILD);
            if (questTab != null && questTab.isVisible()) {
                log.info("Infer: Quest journal open, substate = TALK_GUIDE_2.");
                return SubState.TALK_GUIDE_2;
            }
            var questTabButton = getWidgetSafe(QUEST_TAB_BUTTON_PARENT, QUEST_TAB_BUTTON_CHILD);
            if (questTabButton != null && questTabButton.isVisible()) {
                log.info("Infer: Quest journal tab button visible, substate = OPEN_QUESTS_TAB.");
                return SubState.OPEN_QUESTS_TAB;
            }
        } catch (Exception e) {
            log.warn("Exception in inferSubState: {}", e.toString());
        }
        log.info("Infer: At Quest Guide, ready to TALK_GUIDE_1.");
        return SubState.TALK_GUIDE_1;
    }

    @Override
    public boolean isComplete() {
        boolean complete = plugin.getCurrentState() != JGTutorialIslandState.QUEST_GUIDE;
        log.info("isComplete called: currentState={}, complete={}", plugin.getCurrentState(), complete);
        return complete;
    }
}
