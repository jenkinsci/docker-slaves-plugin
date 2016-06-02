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

import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.lang.Override;
import java.util.logging.Logger;

/**
 * Process launcher which uses docker exec instead of <code>execve</code>
 * Jenkins relies on remoting channel to run commands / process on executor. As Docker can as well be used to run a
 * process remotely, we can just bypass jenkins remoting.
 */
public class DockerLauncher extends Launcher.DecoratedLauncher {
    private static final Logger LOGGER = Logger.getLogger(DockerLauncher.class.getName());

    private final DockerProvisioner provisioner;

    public DockerLauncher(TaskListener listener, VirtualChannel channel, boolean isUnix, DockerProvisioner provisioner)  {
        super(new Launcher.RemoteLauncher(listener, channel, isUnix));
        this.provisioner = provisioner;
    }

    @Override
    public Proc launch(ProcStarter starter) throws IOException {
        try {
            return provisioner.launchBuildProcess(starter, listener);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }
}
