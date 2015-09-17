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

package com.cloudbees.jenkins.plugins.containerslaves;

import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerComputer extends AbstractCloudComputer<DockerSlave> {

    private final Job job;

    private DockerProvisioner provisioner;

    public DockerComputer(DockerSlave dockerSlave, Job job) {
        super(dockerSlave);
        this.job = job;
    }

    /**
     * Create a container provisioner to setup this Jenkins "computer" (aka executor)
     *
     */
    public DockerProvisioner createProvisioner(TaskListener listener) {
        provisioner = getNode().getEngine().buildProvisioner(job, listener);
        return provisioner;
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        super.taskCompleted(executor, task, durationMS);
        terminate();
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
        terminate();
    }

    private void terminate() {
        LOGGER.info("Stopping Docker Slave after build completion");
        setAcceptingTasks(false);
        try {
            if (!Boolean.getBoolean("com.cloudbees.jenkins.plugins.containerslaves.DockerComputer.keepContainer"))
            getNode().terminate();
        } catch (InterruptedException e) {
        } catch (IOException e) {
        }
    }

    public Job getJob() {
        return job;
    }

    public DockerProvisioner getProvisioner() {
        return provisioner;
    }

    private static final Logger LOGGER = Logger.getLogger(DockerComputer.class.getName());

}
