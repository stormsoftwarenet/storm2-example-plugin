package net.storm.plugins.tutorialisland;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.storm.api.plugins.Task;
import net.storm.sdk.game.Combat;
import steps.*;

import javax.inject.Inject;
import java.awt.*;

public class JGTutorialIslandOverlay extends OverlayPanel
{
    private final JGTutorialIslandPlugin plugin;

    @Inject
    public JGTutorialIslandOverlay(JGTutorialIslandPlugin plugin)
    {
        this.plugin = plugin;
        setLayer(OverlayLayer.ABOVE_WIDGETS); // or ALWAYS_ON_TOP
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // Basic box, but you can style as you like
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(220, 0));

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Tutorial Island Bot")
                .right("Debug")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Current Step")
                .right(plugin.getCurrentState().toString())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Tick")
                .right(Integer.toString(plugin.getTickCount()))
                .build());

        for (Task task : plugin.getTasks()) {
            if (plugin.getCurrentState() == JGTutorialIslandState.SURVIVAL_EXPERT && task instanceof SurvivalExpertStep) {
                SurvivalExpertStep s = (SurvivalExpertStep) task;
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Substate")
                        .right(String.valueOf(s.getSubState()))
                        .build());
            }
            if (plugin.getCurrentState() == JGTutorialIslandState.GIELINOR_GUIDE && task instanceof GielinorGuideStep) {
                GielinorGuideStep g = (GielinorGuideStep) task;
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Substate")
                        .right(String.valueOf(g.getSubState()))
                        .build());
            }
            if (plugin.getCurrentState() == JGTutorialIslandState.MASTER_CHEF && task instanceof MasterChefStep) {
                MasterChefStep c = (MasterChefStep) task;
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Substate")
                        .right(String.valueOf(c.getSubState()))
                        .build());
            }
            if (plugin.getCurrentState() == JGTutorialIslandState.QUEST_GUIDE && task instanceof QuestGuideStep) {
                QuestGuideStep q = (QuestGuideStep) task;
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Substate")
                        .right(String.valueOf(q.getSubState()))
                        .build());
            }
            if (plugin.getCurrentState() == JGTutorialIslandState.MINING_INSTRUCTOR && task instanceof MiningGuideStep) {
                MiningGuideStep m = (MiningGuideStep) task;
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Substate")
                        .right(String.valueOf(m.getSubState()))
                        .build());
            }
            if (plugin.getCurrentState() == JGTutorialIslandState.COMBAT_INSTRUCTOR && task instanceof CombatInstructorStep) {
                CombatInstructorStep cb = (CombatInstructorStep) task;
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Substate")
                        .right(String.valueOf(cb.getSubState()))
                        .build());
            }
            if (plugin.getCurrentState() == JGTutorialIslandState.BANKER && task instanceof BankerStep) {
                BankerStep b = (BankerStep) task;
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Substate")
                        .right(String.valueOf(b.getSubState()))
                        .build());
            }
            if (plugin.getCurrentState() == JGTutorialIslandState.PRAYER_INSTRUCTOR && task instanceof PrayerInstructorStep) {
                PrayerInstructorStep p = (PrayerInstructorStep) task;
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Substate")
                        .right(String.valueOf(p.getSubState()))
                        .build());
            }
            if (plugin.getCurrentState() == JGTutorialIslandState.MAGIC_INSTRUCTOR && task instanceof MagicInstructorStep) {
                MagicInstructorStep m = (MagicInstructorStep) task;
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Substate")
                        .right(String.valueOf(m.getSubState()))
                        .build());
            }
        }

        return super.render(graphics);
    }
}
