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
import net.storm.api.domain.tiles.ITileObject;

@Slf4j
public class BankerStep implements TutorialStep {
    private final JGTutorialIslandPlugin plugin;

    // TODO: Replace all these with real IDs and coords
    private static final int BANK_BOOTH_ID = 10083;
    private static final int POLL_BOOTH_ID = 26815;
    private static final int ACCOUNT_GUIDE_ID = 3310;
    private static final int BANK_BOOTH_X = 3122, BANK_BOOTH_Y = 3123, PLANE = 0;
    private static final int POLL_BOOTH_X = 3120, POLL_BOOTH_Y = 3121;
    private static final int ACCOUNT_GUIDE_X = 3125, ACCOUNT_GUIDE_Y = 3124;

    // Widget IDs for Bank and Poll Booth interfaces (fill these in from DevTools if you want to check they're open/close)
    private static final int ACCOUNT_MANAGE_PARENT = 164, ACCOUNT_MANAGE_CHILD = 39;

    private enum SubState {
        WALK_TO_BANK,
        OPEN_BANK,
        CLOSE_BANK_IF_OPEN,
        WALK_TO_POLL_BOOTH,
        OPEN_POLL_BOOTH,
        CLOSE_POLL_IF_OPEN,
        WALK_TO_ACCOUNT_GUIDE,
        TALK_ACCOUNT_GUIDE,
        CLICK_ACCOUNT_MANAGEMENT,
        FINAL_DIALOGUE,
        MOVE_ON
    }
    @Getter
    private SubState subState;

    public BankerStep(JGTutorialIslandPlugin plugin) {
        this.plugin = plugin;
        this.subState = SubState.WALK_TO_BANK;
    }

    private void setSubState(SubState next) {
        if (subState != next) log.info("Changing substate: {} -> {}", subState, next);
        subState = next;
    }

    @Override
    public boolean validate() {
        boolean valid = plugin.getCurrentState() == JGTutorialIslandState.BANKER;
        log.info("Validate called: currentState={}, valid={}", plugin.getCurrentState(), valid);
        return valid;
    }

    @Override
    public int execute() {
        plugin.incrementTick();
        if (!Dialog.isOpen() && !Players.getLocal().isInteracting()) {
            SubState inferred = inferSubState();
            if (inferred != subState) setSubState(inferred);
        }
        log.info("---- BankerStep Tick ---- Current subState: {}", subState);

        switch (subState) {
            case WALK_TO_BANK: {
                if (!isAtBankBooth()) {
                    Movement.walkTo(BANK_BOOTH_X, BANK_BOOTH_Y, PLANE);
                    log.info("Walking to Bank booth.");
                    return randomDelay(1000);
                }
                setSubState(SubState.OPEN_BANK);
                return randomDelay(500);
            }
            case OPEN_BANK: {
                ITileObject bankBooth = getNearestObject(BANK_BOOTH_ID);
                if (bankBooth != null) {
                    bankBooth.interact("Use");
                    log.info("Interacting with Bank booth.");
                    setSubState(SubState.CLOSE_BANK_IF_OPEN);
                    return randomDelay(1200);
                }
                log.warn("Couldn't find bank booth!");
                return randomDelay(600);
            }
            case CLOSE_BANK_IF_OPEN: {
                // Optionally, detect if the bank interface is open and close it, or just wait a second
                if (isBankWidgetOpen()) {
                    closeBankWidget();
                    log.info("Closing bank interface.");
                    setSubState(SubState.WALK_TO_POLL_BOOTH);
                    return randomDelay(1000);
                }
                setSubState(SubState.WALK_TO_POLL_BOOTH);
                return randomDelay(500);
            }
            case WALK_TO_POLL_BOOTH: {
                if (!isAtPollBooth()) {
                    Movement.walkTo(POLL_BOOTH_X, POLL_BOOTH_Y, PLANE);
                    log.info("Walking to Poll booth.");
                    return randomDelay(1000);
                }
                setSubState(SubState.OPEN_POLL_BOOTH);
                return randomDelay(500);
            }
            case OPEN_POLL_BOOTH: {
                ITileObject pollBooth = getNearestObject(POLL_BOOTH_ID);
                if (pollBooth != null) {
                    pollBooth.interact("Use");
                    log.info("Interacting with Poll booth.");
                    setSubState(SubState.CLOSE_POLL_IF_OPEN);
                    return randomDelay(1200);
                }
                log.warn("Couldn't find poll booth!");
                return randomDelay(600);
            }
            case CLOSE_POLL_IF_OPEN: {
                if (isPollWidgetOpen()) {
                    closePollWidget();
                    log.info("Closing poll booth interface.");
                    setSubState(SubState.WALK_TO_ACCOUNT_GUIDE);
                    return randomDelay(1000);
                }
                setSubState(SubState.WALK_TO_ACCOUNT_GUIDE);
                return randomDelay(500);
            }
            case WALK_TO_ACCOUNT_GUIDE: {
                if (!isAtAccountGuide()) {
                    Movement.walkTo(ACCOUNT_GUIDE_X, ACCOUNT_GUIDE_Y, PLANE);
                    log.info("Walking to Account Guide.");
                    return randomDelay(1000);
                }
                setSubState(SubState.TALK_ACCOUNT_GUIDE);
                return randomDelay(500);
            }
            case TALK_ACCOUNT_GUIDE: {
                // If dialog options are up, pick first option
                if (getWidgetSafe(263,1,0).getText().toLowerCase().contains(
                        "continue through the"
                )){
                    setSubState(SubState.MOVE_ON);
                }
                if (getWidgetSafe(263,1,0).getText().toLowerCase()
                        .contains("click on the flashing icon to open your account management"))
                {setSubState((SubState.CLICK_ACCOUNT_MANAGEMENT));}
                // After dialog fully ends, THEN move to next substate
                if (!Dialog.isOpen() && !Dialog.isViewingOptions()) {
                    setSubState(SubState.CLICK_ACCOUNT_MANAGEMENT); // or whatever is next
                    log.info("Dialog with Account Guide finished, moving to ACCOUNT_MANAGEMENT step.");
                    return randomDelay(600);
                }
                if (Dialog.isViewingOptions()) {
                    Dialog.chooseOption(1);
                    log.info("Choosing dialog option 1 for Account Guide.");
                    return randomDelay(400);
                }
                // If dialog open, just continue
                if (Dialog.isOpen() && Dialog.canContinue()) {
                    Dialog.continueSpace();
                    log.info("Continuing dialog with Account Guide.");
                    return randomDelay(200);
                }
                // Only try to talk if NOT already in dialog, and you are at the Account Guide
                INPC guide = getAccountGuide();
                if (guide != null && !Players.getLocal().isInteracting() && isAt(guide)) {
                    guide.interact("Talk-to");
                    log.info("Talking to Account Guide.");
                    return randomDelay(1200);
                }
                return randomDelay(600);
            }
            case CLICK_ACCOUNT_MANAGEMENT: {
                // Check for widget and click if required (account management menu)
                IWidget manageWidget = getWidgetSafe(ACCOUNT_MANAGE_PARENT, ACCOUNT_MANAGE_CHILD);
                if (manageWidget != null && manageWidget.isVisible()) {
                    manageWidget.click();
                    log.info("Clicked Account Management widget.");
                    setSubState(SubState.FINAL_DIALOGUE);
                    return randomDelay(800);
                }
                setSubState(SubState.FINAL_DIALOGUE);
                return randomDelay(500);
            }
            case FINAL_DIALOGUE: {
                if (getWidgetSafe(263,1,0).getText().toLowerCase().contains(
                        "continue through the"
                )){
                    setSubState(SubState.MOVE_ON);
                }
                if (Dialog.isViewingOptions()) {
                    Dialog.chooseOption(1);
                    log.info("Choosing dialog option 1 for Account Guide.");
                    return randomDelay(400);
                }
                // If dialog open, just continue
                if (Dialog.isOpen() && Dialog.canContinue()) {
                    Dialog.continueSpace();
                    log.info("Continuing dialog with Account Guide.");
                    return randomDelay(200);
                }
                INPC guide = getAccountGuide();
                if (guide != null && !Players.getLocal().isInteracting() && isAt(guide)) {
                    guide.interact("Talk-to");
                    log.info("Talking to Account Guide.");
                    return randomDelay(1200);
                }
                setSubState(SubState.MOVE_ON);
                return randomDelay(600);
            }
            case MOVE_ON: {
                log.info("Completed Banker step! Setting plugin state to next step.");
                plugin.setCurrentState(JGTutorialIslandState.PRAYER_INSTRUCTOR); // Set whatever comes next
                return randomDelay(600);
            }
        }
        return randomDelay(600);
    }

    // --- Utility: get nearest bank/poll booth, or account guide NPC ---
    private ITileObject getNearestObject(int id) {
        try {
            return net.storm.sdk.entities.TileObjects.getNearest(id);
        } catch (Exception e) {
            log.warn("getNearestObject({}): {}", id, e.toString());
            return null;
        }
    }

    private INPC getAccountGuide() {
        try {
            return NPCs.query().ids(ACCOUNT_GUIDE_ID).results().nearest(Players.getLocal());
        } catch (Exception e) {
            log.warn("getAccountGuide exception: {}", e.toString());
            return null;
        }
    }

    // --- At-position checks ---
    private boolean isAtBankBooth() {
        if (Players.getLocal() == null || Players.getLocal().getWorldLocation() == null)
            return false;
        int px = Players.getLocal().getWorldLocation().getX();
        int py = Players.getLocal().getWorldLocation().getY();
        return (Math.abs(px - BANK_BOOTH_X) <= 1 && Math.abs(py - BANK_BOOTH_Y) <= 1);
    }

    private boolean isAtPollBooth() {
        if (Players.getLocal() == null || Players.getLocal().getWorldLocation() == null)
            return false;
        int px = Players.getLocal().getWorldLocation().getX();
        int py = Players.getLocal().getWorldLocation().getY();
        return (Math.abs(px - POLL_BOOTH_X) <= 1 && Math.abs(py - POLL_BOOTH_Y) <= 1);
    }

    private boolean isAtAccountGuide() {
        if (Players.getLocal() == null || Players.getLocal().getWorldLocation() == null)
            return false;
        int px = Players.getLocal().getWorldLocation().getX();
        int py = Players.getLocal().getWorldLocation().getY();
        return (Math.abs(px - ACCOUNT_GUIDE_X) <= 1 && Math.abs(py - ACCOUNT_GUIDE_Y) <= 1);
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

    // --- Widget helpers ---
    private IWidget getWidgetSafe(int parent, int child) {
        try { return Widgets.get(parent, child); }
        catch (Exception e) { return null; }
    }
    private IWidget getWidgetSafe(int parent, int child, int index) {
        try { return Widgets.get(parent, child, index); }
        catch (Exception e) { return null; }
    }

    // Check if bank interface is open
    private boolean isBankWidgetOpen() {
        IWidget bankWidget = getWidgetSafe(12, 0);
        return bankWidget != null && bankWidget.isVisible();
    }

    private void closeBankWidget() {
        IWidget closeButton = getWidgetSafe(12, 11);
        if (closeButton != null && closeButton.isVisible()) closeButton.click();
    }

    // Check if poll booth widget is open (usually 310, 2 or 310, 3)
    private boolean isPollWidgetOpen() {
        IWidget pollWidget = getWidgetSafe(310, 2);
        return pollWidget != null && pollWidget.isVisible();
    }

    private void closePollWidget() {
        IWidget closeButton = getWidgetSafe(310, 7); // Poll booth close button usually here
        if (closeButton != null && closeButton.isVisible()) closeButton.click();
    }

    // --- Core state inference, structure as usual ---
    private SubState inferSubState() {
        // Example of sequencing logic
        if (!isAtBankBooth())
            return SubState.WALK_TO_BANK;
        if (!isBankWidgetOpen())
            return SubState.OPEN_BANK;
        if (isBankWidgetOpen())
            return SubState.CLOSE_BANK_IF_OPEN;
        // ... etc
        return subState;
    }

    // --- Misc ---
// Add a base random delay for human-like timing
    private int randomDelay(int base) {
        return base + (int) (Math.random() * 200) - 100;
    }

    // --- Completion ---
    @Override
    public boolean isComplete() {
        // Done if plugin has moved to next state
        boolean complete = plugin.getCurrentState() != JGTutorialIslandState.BANKER;
        log.info("isComplete called: currentState={}, complete={}", plugin.getCurrentState(), complete);
        return complete;
    }
}
