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

import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;

import java.io.IOException;
import java.util.Collections;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerSlave extends AbstractCloudSlave {

    private final DockerEngine engine;
    private final Job job;

    public DockerSlave(Job job, String labelString, DockerEngine engine) throws Descriptor.FormException, IOException {
        super("Container for "+ job.getFullName(), "Container slave for building " + job.getFullName(),
                "/home/jenkins", 1, Mode.EXCLUSIVE, labelString,
                new DockerComputerLauncher(), ONCE, Collections.<NodeProperty<?>>emptyList());
        this.engine = engine;
        this.job = job;
    }

    public AbstractCloudComputer createComputer() {
        return new DockerComputer(this, job);
    }

    public DockerEngine getEngine() {
        return engine;
    }

    public Job getJob() {
        return job;
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {

    }

    private final static RetentionStrategy ONCE = new CloudRetentionStrategy(1);
}
