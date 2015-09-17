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

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * {@link Cloud} implementation designed to launch a set of containers (aka "pod") to establish a Jenkins executor.
 *
 */
public class DockerCloud extends Cloud {

    private final String labelString;

    private final String defaultBuildImage;

    private transient Set<LabelAtom> labels;

    private transient DockerEngine engine;

    @DataBoundConstructor
    public DockerCloud(String name, String labelString, String defaultBuildImage) {
        super(name);
        this.labelString = labelString;
        this.defaultBuildImage = defaultBuildImage;
        parseLabelString();
        engine = new DockerEngine(defaultBuildImage);
    }

    public String getLabelString() {
        return labelString;
    }

    public DockerEngine getEngine() {
        return engine;
    }

    public void parseLabelString() {
        labels = Label.parse(labelString);
    }

    private Object readResolve() {
        parseLabelString();
        engine = new DockerEngine(defaultBuildImage);
        return this;
    }


    @Override
    /**
     * Not just considering delay for NodeProvisioner to call this, we also can't create the Build Pod here as we
     * just don't know which {@link Job} it target, to retrieve the set of container images to launch.
     */
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public boolean canProvision(Label label) {
        return label.matches(labels);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return "Containers Cloud";
        }

    }
}
