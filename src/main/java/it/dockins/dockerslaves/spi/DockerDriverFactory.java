package it.dockins.dockerslaves.spi;

import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Job;
import jenkins.model.Jenkins;

import java.io.IOException;

public abstract class DockerDriverFactory implements ExtensionPoint, Describable<DockerDriverFactory> {

    public abstract DockerDriver forJob(Job context) throws IOException, InterruptedException;

    @Override
    public Descriptor<DockerDriverFactory> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

}
