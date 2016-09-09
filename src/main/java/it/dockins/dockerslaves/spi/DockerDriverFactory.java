package it.dockins.dockerslaves.spi;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Job;

import java.io.IOException;

public abstract class DockerDriverFactory extends AbstractDescribableImpl<DockerDriverFactory> implements ExtensionPoint {

    public abstract DockerDriver forJob(Job context, String remotingImage, String scmImage) throws IOException, InterruptedException;

}
