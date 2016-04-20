/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package xyz.quoidneufdocker.jenkins.dockerslaves.pipeline;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Node;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import java.util.List;
import java.util.Set;

/**
 * Provisions a Docker container and run the closure into it like node would do for a normal node
 *
 * <p>
 * Used like:
 * <pre>
 *     dockerNode(image:"cloudbees/java-tools", sideContainers: ["selenium/standalone-firefox"]) {
 *         // execute some stuff inside this container
 *     }
 * </pre>
 */

public class DockerNodeStep extends AbstractStepImpl {
    private List<String> sideContainers;

    @CheckForNull
    private final String image;

    @DataBoundConstructor
    public DockerNodeStep(String image) {
        this.image = Util.fixEmptyAndTrim(image);
    }

    @CheckForNull
    public String getImage() {
        return this.image;
    }

    public List<String> getSideContainers() {
        return sideContainers;
    }

    @DataBoundSetter
    public void setSideContainers(List<String> sideContainers) {
        this.sideContainers = sideContainers;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(DockerNodeStepExecution.class);
        }

        public String getFunctionName() {
            return "dockerNode";
        }

        public String getDisplayName() {
            return "Allocate a docker node";
        }

        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Set<Class<?>> getProvidedContext() {
            return ImmutableSet.of(Executor.class, Computer.class, FilePath.class, EnvVars.class, Node.class, Launcher.class);
        }
    }
}