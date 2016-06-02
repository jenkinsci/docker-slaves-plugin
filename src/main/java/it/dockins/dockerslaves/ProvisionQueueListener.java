/*
 * The MIT License
 *
 *  Copyright (c) 2015, CloudBees, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */

package it.dockins.dockerslaves;

import it.dockins.dockerslaves.spec.ContainerSetDefinition;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Cloud} API is designed to launch virtual machines, which is an heavy process, so relies on
 * {@link  NodeProvisioner} to determine when a new slave is required. Here we want the slave to start just as a job
 * enter the build queue. As an alternative we listen the Queue for Jobs to get scheduled, and when label match
 * immediately start a fresh new container executor with a unique label to enforce exclusive usage.
 *
 */
@Extension
public class ProvisionQueueListener extends QueueListener {

    @Override
    public void onEnterBuildable(final Queue.BuildableItem item) {
        if (item.task instanceof AbstractProject) {
            AbstractProject job = (AbstractProject) item.task;
            ContainerSetDefinition def = (ContainerSetDefinition) job.getProperty(ContainerSetDefinition.class);
            if (def == null) return;

            try {
                final Node node = prepareExecutorFor(job);

                DockerSlaveAssignmentAction action = new DockerSlaveAssignmentAction(node.getNodeName());
                item.addAction(action);

                Computer.threadPoolForRemoting.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Jenkins.getActiveInstance().addNode(node);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failure to create Docker Slave", e);
                Jenkins.getActiveInstance().getQueue().cancel(item);
            }
        }
    }

    private Node prepareExecutorFor(final AbstractProject job) throws Descriptor.FormException, IOException, InterruptedException {
        LOGGER.info("Creating a Container slave to host " + job.toString() + "#" + job.getNextBuildNumber());

        // Immediately create a slave for this item
        // Real provisioning will happen later
        String slaveName = "Container for " +job.getName() + "#" + job.getNextBuildNumber();
        String description = "Container slave for building " + job.getFullName();
        DockerSlaves plugin = DockerSlaves.get();
        return new DockerSlave(slaveName, description, null, plugin.createStandardJobProvisionerFactory(job));
    }

    /**
     * If item is canceled, remove the executor we created for it.
     */
    @Override
    public void onLeft(Queue.LeftItem item) {
        if (item.isCancelled()) {
            DockerSlaveAssignmentAction action = item.getAction(DockerSlaveAssignmentAction.class);
            if( action == null) return;
            Node slave = action.getAssignedNodeName();
            if (slave == null) return;
            try {
                Jenkins.getActiveInstance().removeNode(slave);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failure to remove One-Shot Slave", e);
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ProvisionQueueListener.class.getName());
}
