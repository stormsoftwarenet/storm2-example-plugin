package net.storm.plugins.tutorialisland;

import com.google.inject.Inject;
import lombok.Getter;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.api.plugins.Task;
import net.storm.sdk.plugins.TaskPlugin;
import net.storm.sdk.script.paint.DefaultPaint;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;
import steps.*;

@PluginDescriptor(
        name = "Tutorial Island Bot",
        description = "Automates Tutorial Island from scratch",
        enabledByDefault = false
)
@Extension
public class JGTutorialIslandPlugin extends TaskPlugin {
    @Getter
    private JGTutorialIslandState currentState = JGTutorialIslandState.GIELINOR_GUIDE;
    @Getter
    private int tickCount = 0;

    @Inject
    private JGTutorialIslandOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    // --- PERSISTENT STEP INSTANCES ---
    private final GielinorGuideStep gielinorGuideStep = new GielinorGuideStep(this);
    private final SurvivalExpertStep survivalExpertStep = new SurvivalExpertStep(this);
    private final MasterChefStep masterChefStep = new MasterChefStep(this);
    private final QuestGuideStep questGuideStep = new QuestGuideStep(this);
    private final MiningGuideStep miningGuideStep = new MiningGuideStep(this);
    private final  CombatInstructorStep combatInstructorStep = new CombatInstructorStep(this);
    private final BankerStep bankerStep = new BankerStep(this);
    private final PrayerInstructorStep prayerInstructorStep = new PrayerInstructorStep(this);
    private final MagicInstructorStep magicInstructorStep = new MagicInstructorStep(this);
    @Override
    public void startUp() {
        overlayManager.add(overlay);
        setCurrentState(JGTutorialIslandState.GIELINOR_GUIDE);
    }

    @Override
    public void shutDown() {
        overlayManager.remove(overlay);
    }

    // Return the steps only ONCE; but you will only run the ACTIVE one
    @Override
    public Task[] getTasks() {
        return new Task[] {
                gielinorGuideStep,
                survivalExpertStep,
                masterChefStep,
                questGuideStep,
                miningGuideStep,
                combatInstructorStep,
                bankerStep,
                prayerInstructorStep,
                magicInstructorStep,


        };
    }

    public void incrementTick() {
        tickCount++;
    }

    public void resetTickCount() {
        tickCount = 0;
    }

    public void setCurrentState(JGTutorialIslandState newState) {
        if (this.currentState != newState) {
            System.out.println("Tutorial Island State changed: " + this.currentState + " → " + newState);
            this.currentState = newState;
            resetTickCount();
            // Optionally: update overlay, log, trigger events, etc.
        }
    }

    // --- Only run the active step ---
    @Override
    protected int loop() {
        // Only execute the step where validate() is true (should only be one)
        for (Task task : getTasks()) {
            if (task instanceof steps.TutorialStep && ((steps.TutorialStep) task).validate()) {
                return task.execute();
            }
        }
        // If no step matched, just sleep
        return 600;
    }
}
