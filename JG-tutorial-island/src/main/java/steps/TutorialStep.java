package steps;

import net.storm.api.plugins.Task;

public interface TutorialStep extends Task {
boolean isComplete();
}