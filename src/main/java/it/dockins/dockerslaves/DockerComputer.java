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

import it.dockins.dockerslaves.api.OneShotComputer;
import hudson.slaves.ComputerLauncher;
import it.dockins.dockerslaves.spi.DockerProvisioner;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * A computer on which a specific build will occur
 */
public class DockerComputer extends OneShotComputer {

    private final DockerSlave slave;

    private final DockerProvisioner provisioner;

    public DockerComputer(DockerSlave slave, DockerProvisioner provisioner) {
        super(slave);
        this.provisioner = provisioner;
        this.slave = slave;
    }

    @Override
    public DockerSlave getNode() {
        return slave;
    }

    @Override
    public Boolean isUnix() {
        return Boolean.TRUE;
    }

    @Override
    protected void terminate() {
        LOGGER.info("Stopping Docker Slave after build completion");
        setAcceptingTasks(false);
        try {
            provisioner.clean(getListener());
        } catch (InterruptedException e) {
            e.printStackTrace(); //FIXME
        } catch (IOException e) {
            e.printStackTrace(); //FIXME
        }
        super.terminate();
    }

    public DockerProvisioner getProvisioner() {
        return provisioner;
    }

    private static final Logger LOGGER = Logger.getLogger(DockerComputer.class.getName());

    public ComputerLauncher createComputerLauncher() {
        return new DockerComputerLauncher();
    }
}
