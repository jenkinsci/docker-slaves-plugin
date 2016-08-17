package it.dockins.dockerslaves;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Queue;
import it.dockins.dockerslaves.spec.ContainerSetDefinition;
import org.jenkinsci.plugins.oneshot.OneShotProvisioner;
import org.jenkinsci.plugins.oneshot.OneShotSlave;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class DockerSlaveProvisioner extends OneShotProvisioner<DockerSlave> {

    @Override
    protected boolean usesOneShotExecutor(Queue.Item item) {
        final Queue.Task task = item.task;
        if (task instanceof Job) {
            final JobProperty property = ((Job) task).getProperty(ContainerSetDefinition.class);
            return property != null;
        }
        return false;
    }

    @Override
    public boolean canRun(Queue.Item item) {
        return true;
    }

    @Override
    public OneShotSlave prepareExecutorFor(Queue.BuildableItem item) throws Exception {
        final Queue.Task task = item.task;
        String desc = "Docker slave for building " + task.getFullDisplayName();
        if (task instanceof Job) {
            return new DockerSlave(desc, DockerSlaves.get().createStandardJobProvisionerFactory((Job) task));
        }
        throw new IllegalStateException("Only available for Jobs");
    }
}
