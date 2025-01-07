package net.storm.plugins.examples.task;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.api.plugins.Task;
import net.storm.api.plugins.config.ConfigManager;
import net.storm.plugins.examples.task.tasks.AttackNpc;
import net.storm.plugins.examples.task.tasks.EatFood;
import net.storm.sdk.plugins.TaskPlugin;
import org.pf4j.Extension;

/*
 * A very basic example of a task-based plugin.
 *
 * Important notes: look at the imports! The class names are similar to RuneLite's API, but they are not the same.
 * Always use the Storm SDK's classes when developing plugins.
 *
 * Ensure that your package names start with net.storm.plugins, or your plugin will not be compatible with the SDN.
 */
@PluginDescriptor(name = "Example Task Plugin")
@Extension
public class ExampleTaskPlugin extends TaskPlugin {
    private Task[] tasks;

    @Inject
    private ExampleTaskConfig config;

    @Override
    public Task[] getTasks() {
        return tasks;
    }

    @Override
    public void startUp() throws Exception {
        tasks = new Task[]{
                new EatFood(config), // task order is important here, if the first task validates, it will block the execution of the next tasks
                new AttackNpc(config),
        };
    }

    @Provides
    public ExampleTaskConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ExampleTaskConfig.class);
    }
}
