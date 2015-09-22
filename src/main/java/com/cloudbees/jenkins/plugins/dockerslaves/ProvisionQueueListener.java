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

package com.cloudbees.jenkins.plugins.dockerslaves;

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
    public void onEnterBuildable(final Queue.BuildableItem bi) {
        if (bi.task instanceof AbstractProject) {
            AbstractProject job = (AbstractProject) bi.task;
            JobBuildsContainersDefinition def = (JobBuildsContainersDefinition) job.getProperty(JobBuildsContainersDefinition.class);
            if (def == null) return;

            final DockerSlaves cloud = DockerSlaves.get();

            LOGGER.info("Creating a Container slave to host " + bi.toString());
            DockerLabelAssignmentAction action = cloud.createLabelAssignmentAction(bi);
            bi.addAction(action);

            // Immediately create a slave for this item
            // Real provisioning will happen later

            try {
                final Node node = new DockerSlave(job, action.getLabel().toString());
                Computer.threadPoolForRemoting.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Jenkins.getInstance().addNode(node);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Descriptor.FormException e) {
                e.printStackTrace();
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ProvisionQueueListener.class.getName());
}
