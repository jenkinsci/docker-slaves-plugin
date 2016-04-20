package xyz.quoidneufdocker.jenkins.dockerslaves.api;

import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * ComputerLauncher that delegates to a launcher created on first use
 */
public class DeferredComputerLauncher extends ComputerLauncher {
    protected ComputerLauncher delegate = null;
    protected Callable<ComputerLauncher> computerLauncherFactory;

    public DeferredComputerLauncher(Callable<ComputerLauncher> computerLauncherFactory) {
        this.computerLauncherFactory = computerLauncherFactory;
    }

    protected synchronized ComputerLauncher getDelegate() {
        if (delegate == null) {
            try {
                delegate = computerLauncherFactory.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return delegate;
    }

    @Override
    public boolean isLaunchSupported() {
        return getDelegate().isLaunchSupported();
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        getDelegate().launch(computer, listener);
    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        getDelegate().afterDisconnect(computer, listener);
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
        getDelegate().beforeDisconnect(computer, listener);
    }
}
