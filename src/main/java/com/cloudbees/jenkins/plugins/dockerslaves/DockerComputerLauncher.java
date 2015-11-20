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

import hudson.model.FreeStyleBuild;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 * Launchs initials containers
 */
public class DockerComputerLauncher extends ComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(DockerComputerLauncher.class.getName());

    public DockerComputerLauncher() {
    }

    @Override
    public void launch(final SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        if (computer instanceof DockerComputer) {
            launch((DockerComputer) computer, listener);
        } else {
            throw new IllegalArgumentException("This launcher only can handle DockerComputer");
        }
    }

    private void writeTagBegin(OutputStream out, String tag) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("<").append(tag).append(">");
        out.write(builder.toString().getBytes(Charsets.UTF_8));
    }

    private void writeTagEnd(OutputStream out, String tag) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("</").append(tag).append(">\n");
        out.write(builder.toString().getBytes(Charsets.UTF_8));
    }

    private void writeTag(OutputStream out, String tag, String value) throws IOException {
        writeTagBegin(out, tag);
        StringBuilder builder = new StringBuilder();
        builder.append(value);
        out.write(builder.toString().getBytes(Charsets.UTF_8));
        writeTagEnd(out, tag);
    }

    private static final String XML_HEADER = "<?xml version='1.0' encoding='UTF-8'?>\n";

    private void writeFakeBuild(File file, Run run) throws IOException {
        String topLevelTag = run instanceof FreeStyleBuild ? "build" : run.getClass().getCanonicalName();

        FileOutputStream fos = new FileOutputStream(file);
        try {
            fos.write(XML_HEADER.getBytes(Charsets.UTF_8));
            writeTagBegin(fos, topLevelTag);
            writeTag(fos, "timestamp", Long.toString(System.currentTimeMillis()));
            writeTag(fos, "startTime", Long.toString(System.currentTimeMillis()));
            writeTag(fos, "result", Result.NOT_BUILT.toString());
            writeTag(fos, "duration", "0");
            writeTag(fos, "keepLog", "false");
            writeTagEnd(fos, topLevelTag);
        } finally {
            IOUtils.closeQuietly(fos);
        }
    }

    private void writeLog(File file, TeeTaskListener teeListener) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        try {
            teeListener.setSideOutputStream(fos);
        } finally {
            IOUtils.closeQuietly(fos);
        }
    }

    protected void recordFailureOnBuild(final DockerComputer computer, TeeTaskListener teeListener, IOException e) throws IOException, InterruptedException {
        Queue.Item queued = computer.getJob().getQueueItem();
        Jenkins.getInstance().getQueue().cancel(queued);
        Queue.Executable executable = queued.task.createExecutable();
        if (executable instanceof Run) {
            Run run = ((Run) executable);
            writeFakeBuild(new File(run.getRootDir(),"build.xml"), run);
            writeLog(new File(run.getRootDir(),"log"), teeListener);
            run.reload();
        } else {
            // find a way to undo the previous createExecutable....
        }
    }

    public void launch(final DockerComputer computer, TaskListener listener) throws IOException, InterruptedException {
        // we need to capture taskListener here, as it's a private field of Computer
        TeeTaskListener teeListener = computer.initTeeListener(listener);

        DockerJobContainersProvisioner provisioner = computer.createProvisioner();
        try {
            provisioner.prepareRemotingContainer();
            provisioner.launchRemotingContainer(computer, teeListener);
        } catch (IOException e) {
            e.printStackTrace(teeListener.getLogger());
            computer.terminate();
            recordFailureOnBuild(computer, teeListener, e);
            throw e;
        }
    }
}
