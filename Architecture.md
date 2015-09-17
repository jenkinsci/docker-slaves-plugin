# Architecture

## Why not Cloud API ?

Jenkins `hudson.slaves.Cloud` is designed to manage virtual machines, i.e. heavy to bootstrap, (relatively) long lived. 
As we manage containers, which are fast to start (as long as image is available on host) and designed to only host a single build,
the API doesn't strictly match our needs.

## Build Pod

The Jenkins slave is created as a set of containers, a.k.a. "pod", oen of them being used to establish the jenkins remoting 
communication channel, the others to run build steps, sharing volumes and network. This set of container is internaly documented
as `com.cloudbees.jenkins.plugins.containerslaves.DockerBuildContext`.

## Provisioner

Jenkins relies on `hudson.slaves.NodeProvisioner` to determine if and when a new executor has to be created by the
`hudson.slaves.Cloud` implementation. This introduces unecessary delay provisioning our container slave (pod).
As a workaround, we register a custom `hudson.model.queue.QueueListener` to be notified just as a job enter the build queue,
then can create the required containers without delay. It also assign a unique `hudson.model.Label`, to ensure this container
will only run once and assigned to this exact build.

## Container Provisioner 

`com.cloudbees.jenkins.plugins.containerslaves.DockerProvisioner` is responsible to create the build pob. For this purpose it
relies on a single, common, slave remoting image. `com.cloudbees.jenkins.plugins.containerslaves.DockerDriver` do handle the 
technical details

## Security

TODO



