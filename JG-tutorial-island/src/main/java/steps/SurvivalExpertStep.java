package steps;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.plugins.tutorialisland.JGTutorialIslandPlugin;
import net.storm.plugins.tutorialisland.JGTutorialIslandState;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Widgets;
import net.storm.sdk.game.Client;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.items.Inventory;

@Slf4j
public class SurvivalExpertStep implements TutorialStep {
    private final JGTutorialIslandPlugin plugin;

    private static final int SURVIVAL_EXPERT_ID = 8503;
    private static final int FISHING_SPOT_ID = 3317;
    private static final int TREE_ID = 9730;
    private static final int DOOR_ID = 9398;
    private static final int PROXIMITY_RADIUS = 2;
    private static final int FIREMAKING_ANIMATION = 733;

    private static final int INVENTORY_TAB_PARENT = 164, INVENTORY_TAB_CHILD = 55;
    private static final int SKILLS_TAB_PARENT = 164, SKILLS_TAB_CHILD = 53;
    private static final int TUTORIAL_WIDGET_PARENT = 263, TUTORIAL_WIDGET_CHILD1 = 1, TUTORIAL_WIDGET_CHILD2 = 0;

    private static final String[] RAW_SHRIMP_NAMES = {"Raw shrimps", "Raw shrimp"};
    private static final String[] COOKED_SHRIMP_NAMES = {"Shrimps", "Shrimp"};
    private static final String[] LOG_NAMES = {"Logs"};
    private static final String[] AXE_NAMES = {"Bronze axe"};
    private static final String[] TINDERBOX_NAMES = {"Tinderbox"};

    private boolean choppedTreeForLogs = false; // Set to true only if YOU chopped the tree

    private enum SubState {
        WALK_TO_EXPERT,
        TALK_EXPERT_1,
        OPEN_INVENTORY,
        FISH,
        OPEN_SKILLS,
        TALK_EXPERT_2,
        CHOP_TREE,
        LIGHT_FIRE,
        COOK_SHRIMP,
        MOVE_ON
    }

    @Getter
    private SubState subState;

    public SurvivalExpertStep(JGTutorialIslandPlugin plugin) {
        this.plugin = plugin;
        this.subState = SubState.WALK_TO_EXPERT;
    }

    private void setSubState(SubState next) {
        if (subState != next) log.info("Changing substate: {} -> {}", subState, next);
        subState = next;
    }

    private int randomDelay(int base) {
        return base + (int) (Math.random() * 200) - 100;
    }

    @Override
    public boolean validate() {
        boolean valid = plugin.getCurrentState() == JGTutorialIslandState.SURVIVAL_EXPERT;
        log.info("Validate called: currentState={}, valid={}", plugin.getCurrentState(), valid);
        return valid;
    }

    @Override
    public int execute() {
        plugin.incrementTick();

        // Re-infer state if not talking/interacting (don’t return, let switch run)
        if (!Dialog.isOpen() && !Players.getLocal().isInteracting()) {
            SubState inferred = inferSubState();
            if (inferred != subState) setSubState(inferred);
        }

        log.info("---- SurvivalExpertStep Tick ---- Current subState: {}", subState);

        switch (subState) {
            case WALK_TO_EXPERT: {
                if (openStartingDoorIfNecessary()) return randomDelay(1200);
                INPC expert = getExpert();
                if (expert == null) {
                    log.warn("Can't find Survival Expert NPC");
                    return randomDelay(600);
                }
                if (!isAt(expert)) {
                    Movement.walkTo(expert.getWorldLocation());
                    log.info("Walking to Survival Expert at {}", expert.getWorldLocation());
                    return randomDelay(1000);
                }
                log.info("Arrived at Survival Expert. Switching to TALK_EXPERT_1");
                setSubState(SubState.TALK_EXPERT_1);
                return randomDelay(600);
            }
            case TALK_EXPERT_1: {
                if (handleDialog()) return randomDelay(200);

                if (hasShrimp()) {
                    log.info("Detected shrimp in inventory, skipping to OPEN_SKILLS.");
                    setSubState(SubState.OPEN_SKILLS);
                    return randomDelay(600);
                }
                if (hasTutorialText("you've been given an item")) {
                    setSubState(SubState.OPEN_INVENTORY);
                    log.info("Infer: Open inventory");
                    return randomDelay(600);
                }
                INPC arrowNpc1 = Client.getHintArrowNpc();
                INPC expert1 = getExpert();
                if ((arrowNpc1 != null && arrowNpc1.getId() == SURVIVAL_EXPERT_ID && !Players.getLocal().isInteracting()) ||
                        (arrowNpc1 == null && isAt(expert1) && !Players.getLocal().isInteracting())) {
                    log.info("Hint arrow or fallback: Interacting with Survival Expert for first talk...");
                    if (arrowNpc1 != null) arrowNpc1.interact("Talk-to");
                    else if (expert1 != null) expert1.interact("Talk-to");
                    return randomDelay(1200);
                }
                if (dialogueJustFinished()) {
                    log.info("Finished initial Survival Expert dialogue. Advancing to OPEN_INVENTORY.");
                    setSubState(SubState.OPEN_INVENTORY);
                    return randomDelay(600);
                }
                return randomDelay(600);
            }
            case OPEN_INVENTORY: {
                var invTab = Widgets.get(INVENTORY_TAB_PARENT, INVENTORY_TAB_CHILD);
                if (invTab != null && invTab.isVisible()) {
                    invTab.click();
                    log.info("Clicked inventory tab ({},{})", INVENTORY_TAB_PARENT, INVENTORY_TAB_CHILD);
                    setSubState(SubState.FISH);
                    return randomDelay(600);
                }
                if (hasShrimp()) {
                    setSubState(SubState.OPEN_SKILLS);
                    log.info("Already have shrimp, skipping to OPEN_SKILLS.");
                    return randomDelay(600);
                }
                return randomDelay(600);
            }
            case FISH: {
                if (hasTutorialText("you've gained some experience")) {
                    log.info("Widget shows 'you've gained some experience'—skipping to OPEN_SKILLS.");
                    setSubState(SubState.OPEN_SKILLS);
                    return randomDelay(600);
                }
                if (handleDialog()) return randomDelay(200);

                if (!hasShrimp()) {
                    INPC fishingSpot = getFishingSpot();
                    if (fishingSpot != null && !Players.getLocal().isInteracting()) {
                        fishingSpot.interact("Net");
                        log.info("Fishing at spot...");
                        return randomDelay(2000);
                    }
                    log.info("Fishing spot not found.");
                } else {
                    setSubState(SubState.OPEN_SKILLS);
                    log.info("Caught shrimp. Proceeding to OPEN_SKILLS.");
                }
                return randomDelay(600);
            }
            case OPEN_SKILLS: {
                var skillsTab = Widgets.get(SKILLS_TAB_PARENT, SKILLS_TAB_CHILD);
                if (skillsTab != null && skillsTab.isVisible()) {
                    skillsTab.click();
                    log.info("Clicked skills tab ({},{})", SKILLS_TAB_PARENT, SKILLS_TAB_CHILD);
                    setSubState(SubState.TALK_EXPERT_2);
                    return randomDelay(600);
                }
                if (hasAxeAndTinderbox()) {
                    setSubState(SubState.CHOP_TREE);
                    log.info("Already have axe/tinderbox, skipping to CHOP_TREE.");
                    return randomDelay(600);
                }
                return randomDelay(600);
            }
            case TALK_EXPERT_2: {
                if (handleDialog()) return randomDelay(200);

                if (hasAxeAndTinderbox()) {
                    log.info("Axe and tinderbox detected, skipping to CHOP_TREE.");
                    setSubState(SubState.CHOP_TREE);
                    return randomDelay(600);
                }
                INPC expert2 = getExpert();
                INPC arrowNpc2 = Client.getHintArrowNpc();
                if ((arrowNpc2 != null && arrowNpc2.getId() == SURVIVAL_EXPERT_ID && !Players.getLocal().isInteracting()) ||
                        (arrowNpc2 == null && isAt(expert2) && !Players.getLocal().isInteracting())) {
                    log.info("Interacting with Survival Expert for axe/tinderbox (TALK_EXPERT_2).");
                    if (arrowNpc2 != null) arrowNpc2.interact("Talk-to");
                    else if (expert2 != null) expert2.interact("Talk-to");
                    return randomDelay(1200);
                }
                log.info("Waiting for axe/tinderbox. (TALK_EXPERT_2)");
                return randomDelay(600);
            }
            case CHOP_TREE: {
                // Defensive: must have axe and tinderbox
                if (!hasAxeAndTinderbox()) {
                    log.info("Lost axe or tinderbox! Reverting to TALK_EXPERT_2.");
                    setSubState(SubState.TALK_EXPERT_2);
                    choppedTreeForLogs = false;
                    return randomDelay(600);
                }
                if (!hasShrimp()) {
                    log.info("Lost shrimp before chopping tree! Returning to FISH.");
                    setSubState(SubState.FISH);
                    choppedTreeForLogs = false;
                    return randomDelay(600);
                }
                if (handleDialog()) return randomDelay(200);

                // Only proceed to LIGHT_FIRE if the logs in inventory are from chopping *this* tree
                if (!hasLogs()) {
                    ITileObject tree = getTree();
                    if (tree != null && !Players.getLocal().isInteracting()) {
                        tree.interact("Chop down");
                        choppedTreeForLogs = true; // Mark: you did the chop, next state must own these logs!
                        log.info("Chopping tree for logs...");
                        return randomDelay(1800);
                    }
                    log.info("Tree not found.");
                } else if (choppedTreeForLogs) {
                    setSubState(SubState.LIGHT_FIRE);
                    log.info("Obtained logs from your own chop. Proceeding to LIGHT_FIRE.");
                } else {
                    log.info("Logs found, but not from your chop. Ensuring you perform the chop before moving on.");
                }
                return randomDelay(600);
            }
            case LIGHT_FIRE: {
                if (!hasAxeAndTinderbox()) {
                    log.info("Lost axe/tinderbox before lighting fire. Returning to TALK_EXPERT_2.");
                    setSubState(SubState.TALK_EXPERT_2);
                    choppedTreeForLogs = false;
                    return randomDelay(600);
                }
                if (!hasShrimp()) {
                    log.info("Lost shrimp before lighting fire! Returning to FISH.");
                    setSubState(SubState.FISH);
                    choppedTreeForLogs = false;
                    return randomDelay(600);
                }
                if (!hasLogs() && !isFiremaking()) {
                    log.info("No logs and not firemaking during LIGHT_FIRE, switching to CHOP_TREE.");
                    setSubState(SubState.CHOP_TREE);
                    return randomDelay(400);
                }
                if (handleDialog()) return randomDelay(200);

                ITileObject fireToCook = getFire();
                if (fireToCook == null) {
                    if (useTinderboxOnLogs()) {
                        log.info("Using tinderbox on logs to light fire...");
                        choppedTreeForLogs = false; // Consumed logs
                        return randomDelay(1500);
                    }
                    log.info("Failed to light fire (check logic)");
                } else {
                    setSubState(SubState.COOK_SHRIMP);
                    log.info("Fire detected. Proceeding to COOK_SHRIMP.");
                }
                return randomDelay(600);
            }
            case COOK_SHRIMP: {
                if (!hasAxeAndTinderbox()) {
                    log.info("Lost axe/tinderbox before cooking. Returning to TALK_EXPERT_2.");
                    setSubState(SubState.TALK_EXPERT_2);
                    return randomDelay(600);
                }
                if (!hasShrimp()) {
                    log.info("No shrimp found during COOK_SHRIMP, need to FISH again.");
                    setSubState(SubState.FISH);
                    return randomDelay(400);
                }
                if (handleDialog()) return randomDelay(200);

                if (!hasCookedShrimp()) {
                    ITileObject fireToCook = getFire();
                    if (fireToCook != null && useShrimpOnFire()) {
                        log.info("Cooking shrimp on fire...");
                        return randomDelay(1500);
                    } else if (fireToCook == null) {
                        log.info("No fire detected! Returning to LIGHT_FIRE.");
                        setSubState(SubState.LIGHT_FIRE);
                        return randomDelay(600);
                    }
                    log.info("Failed to cook shrimp (missing shrimp or fire?)");
                } else {
                    setSubState(SubState.MOVE_ON);
                    log.info("Cooked shrimp. Ready to move on.");
                }
                return randomDelay(600);
            }
            case MOVE_ON: {
                log.info("Completed Survival Expert step! Setting plugin state to MASTER_CHEF");
                plugin.setCurrentState(JGTutorialIslandState.MASTER_CHEF);
                return randomDelay(600);
            }
        }
        return randomDelay(600);
    }

    // ---- Null-safe helpers ----
    private boolean hasShrimp() {
        try { return Inventory.contains(RAW_SHRIMP_NAMES); } catch (Exception e) { return false; }
    }
    private boolean hasAxeAndTinderbox() {
        try { return Inventory.containsAll(AXE_NAMES) && Inventory.containsAll(TINDERBOX_NAMES); } catch (Exception e) { return false; }
    }
    private boolean hasLogs() {
        try { return Inventory.contains(LOG_NAMES); } catch (Exception e) { return false; }
    }
    private boolean hasCookedShrimp() {
        try { return Inventory.contains(COOKED_SHRIMP_NAMES); } catch (Exception e) { return false; }
    }
    private boolean useTinderboxOnLogs() {
        IInventoryItem tinderbox = null, logs = null;
        try {
            tinderbox = Inventory.getFirst(TINDERBOX_NAMES);
            logs = Inventory.getFirst(LOG_NAMES);
        } catch (Exception ignore) {}
        if (tinderbox != null && logs != null) {
            log.info("Using tinderbox on logs...");
            tinderbox.useOn(logs);
            return true;
        }
        return false;
    }
    private boolean useShrimpOnFire() {
        IInventoryItem shrimp = null;
        ITileObject fire = null;
        try {
            shrimp = Inventory.getFirst(RAW_SHRIMP_NAMES);
            fire = getFire();
        } catch (Exception ignore) {}
        if (shrimp != null && fire != null) {
            log.info("Using shrimp on fire...");
            shrimp.useOn(fire);
            return true;
        }
        return false;
    }
    private boolean isFiremaking() {
        try { return Players.getLocal() != null && Players.getLocal().getAnimation() == FIREMAKING_ANIMATION; } catch (Exception e) { return false; }
    }
    private INPC getExpert() {
        try { return NPCs.query().ids(SURVIVAL_EXPERT_ID).results().nearest(Players.getLocal()); } catch (Exception e) { return null; }
    }
    private INPC getFishingSpot() {
        try { return NPCs.query().ids(FISHING_SPOT_ID).results().nearest(Players.getLocal()); } catch (Exception e) { return null; }
    }
    private ITileObject getTree() {
        try { return TileObjects.getNearest(TREE_ID); } catch (Exception e) { return null; }
    }
    private ITileObject getFire() {
        try { return TileObjects.getNearest("Fire"); } catch (Exception e) { return null; }
    }
    private boolean isAt(INPC npc) {
        try {
            return npc != null &&
                    Players.getLocal() != null &&
                    Players.getLocal().getWorldLocation() != null &&
                    npc.getWorldLocation() != null &&
                    Players.getLocal().getWorldLocation().distanceTo(npc.getWorldLocation()) <= PROXIMITY_RADIUS;
        } catch (Exception e) {
            return false;
        }
    }
    private boolean openStartingDoorIfNecessary() {
        try {
            INPC guide = NPCs.query().ids(3308).results().nearest(Players.getLocal());
            if (guide != null && guide.getWorldLocation() != null && Players.getLocal() != null &&
                    Players.getLocal().getWorldLocation() != null &&
                    guide.getWorldLocation().distanceTo(Players.getLocal().getWorldLocation()) < 6) {
                ITileObject door = TileObjects.getNearest(DOOR_ID);
                if (door != null && door.isInteractable()) {
                    door.interact("Open");
                    log.info("Opening starting door...");
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Exception in openStartingDoorIfNecessary: {}", e.toString());
        }
        return false;
    }

    // Unified dialog handler returns true if dialog was handled
    private boolean handleDialog() {
        try {
            if (Dialog.isViewingOptions()) {
                Dialog.chooseOption(1);
                log.info("Choosing first dialog option.");
                return true;
            }
            if (Dialog.isOpen() && Dialog.canContinue()) {
                Dialog.continueSpace();
                log.info("Continuing dialog.");
                return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    // Widget cues for dialogue completion
    private boolean dialogueJustFinished() {
        try {
            var w = Widgets.get(TUTORIAL_WIDGET_PARENT, TUTORIAL_WIDGET_CHILD1, TUTORIAL_WIDGET_CHILD2);
            if (w != null && w.isVisible()) {
                String t = w.getText();
                if (t != null && (t.toLowerCase().contains("you've been given an item") ||
                        t.toLowerCase().contains("open your inventory"))) {
                    log.info("Detected inventory widget after expert dialogue: '{}'", t);
                    return true;
                }
            }
        } catch (Exception ignore) {}
        return false;
    }

    private boolean hasTutorialText(String search) {
        try {
            var w = Widgets.get(TUTORIAL_WIDGET_PARENT, TUTORIAL_WIDGET_CHILD1, TUTORIAL_WIDGET_CHILD2);
            if (w != null && w.isVisible() && w.getText() != null) {
                return w.getText().toLowerCase().contains(search.toLowerCase());
            }
        } catch (Exception ignore) {}
        return false;
    }

    // Substate inference: always null-safe, inventory always overrides widget!
    private SubState inferSubState() {
        try {
            var w = Widgets.get(TUTORIAL_WIDGET_PARENT, TUTORIAL_WIDGET_CHILD1, TUTORIAL_WIDGET_CHILD2);
            if (hasCookedShrimp()) {
                log.info("Infer: Cooked shrimp in inventory, ready to MOVE_ON.");
                return SubState.MOVE_ON;
            }
            if (w != null && w.isVisible() && w.getText().toLowerCase().contains("you've been given an item")) {
                log.info("Infer: Open inventory");
                return SubState.OPEN_INVENTORY;
            }
            if (!hasAxeAndTinderbox()) {
                log.info("Infer: Missing axe or tinderbox, need to TALK_EXPERT_2.");
                return SubState.TALK_EXPERT_2;
            }
            if (!hasShrimp()) {
                log.info("Infer: Missing shrimp, need to FISH.");
                return SubState.FISH;
            }
            // Only move to LIGHT_FIRE if logs were chopped by player
            if (hasLogs() && choppedTreeForLogs) {
                log.info("Infer: Has logs (from own chop), ready to LIGHT_FIRE.");
                return SubState.LIGHT_FIRE;
            }
            if (!hasLogs()) {
                log.info("Infer: Missing logs, need to CHOP_TREE.");
                return SubState.CHOP_TREE;
            }
            // Widget cues
            if (w != null && w.isVisible()) {
                String t = w.getText();
                log.info("Widget text: '{}'", t);
                if (t != null) {
                    t = t.toLowerCase();
                    if (t.contains("catch some shrimp")) {
                        log.info("Infer: Widget says 'catch some shrimp', subState=FISH");
                        return SubState.FISH;
                    }
                    if (t.contains("you've gained some experience") || t.contains("check your skills") || t.contains("view the skills")) {
                        log.info("Infer: Widget says 'gained experience' or 'check your skills', subState=OPEN_SKILLS");
                        return SubState.OPEN_SKILLS;
                    }
                    if (t.contains("on this menu you can view your skills")) {
                        log.info("Infer: Widget says 'talk to your instructor', subState=TALK_EXPERT_2");
                        return SubState.TALK_EXPERT_2;
                    }
                    if (t.contains("light a fire")) {
                        log.info("Infer: Widget says 'light a fire', subState=LIGHT_FIRE");
                        return SubState.LIGHT_FIRE;
                    }
                    if (t.contains("woodcutting")) {
                        log.info("Infer: Widget says 'woodcutting', subState=CHOP_TREE");
                        return SubState.CHOP_TREE;
                    }
                }
            }
            // Fallback
            INPC expert = getExpert();
            if (expert == null || !isAt(expert)) {
                log.info("Infer: Not at expert, need to walk there.");
                return SubState.WALK_TO_EXPERT;
            }
        } catch (Exception e) {
            log.warn("Exception in inferSubState: {}", e.toString());
        }
        log.info("Infer: Defaulting to TALK_EXPERT_1.");
        return SubState.TALK_EXPERT_1;
    }

    @Override
    public boolean isComplete() {
        boolean complete = plugin.getCurrentState() != JGTutorialIslandState.SURVIVAL_EXPERT;
        log.info("isComplete called: currentState={}, complete={}", plugin.getCurrentState(), complete);
        return complete;
    }
}
