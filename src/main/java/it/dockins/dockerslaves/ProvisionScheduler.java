package it.dockins.dockerslaves;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import it.dockins.dockerslaves.spec.ContainerSetDefinition;
import jenkins.model.Jenkins;

/**
 * Responsible for allowing tasks to go into buildable state.
 */
@Extension
public class ProvisionScheduler extends QueueTaskDispatcher {

    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        if (item.task instanceof AbstractProject) {
            AbstractProject job = (AbstractProject) item.task;
            ContainerSetDefinition def = (ContainerSetDefinition) job.getProperty(ContainerSetDefinition.class);
            if (def == null) {
                return null;
            }

            int slaveCount = 0;
            DockerSlaves plugin = DockerSlaves.get();
            for (Node node : Jenkins.getInstance().getNodes()) {
                if (node instanceof DockerSlave) {
                    if (((DockerSlave)node).getQueueItemId() == item.getId()) {
                        return null;
                    }
                    slaveCount++;
                }
            }

            if (slaveCount >= plugin.getMaxContainers()) {
                return new WaitForADockerSlot();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    static final class WaitForADockerSlot extends CauseOfBlockage {
        private WaitForADockerSlot() {
        }

        public String getShortDescription() {
            return "Waiting for a Docker slot";
        }
    }
}
