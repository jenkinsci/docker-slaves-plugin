# container-slaves-plugin

This plugin allows to execute a jenkins job inside a (set of) container(s).

## Status

Planning (to be worked during Global Hack Day). Don't run in production. Use at your own risk. etc.

## General Design

Global configuration let administrator setup the container infrastructure. Typically, a DockerHost URL, but could be extended by third party plugin to connect to another container hosting service, for sample to adapt to Kubernetes Pod API or Rkt container engine. Just need to be [opencontainer](https://www.opencontainers.org/) compliant.

To host a build, plugin will :
* create a data container to host the project workspace.
* run a predefined slave container which is designed to just establish jenkins remoting channel. 
* run a (set of) containers configured by user as part of the job configuration. All them are linked together and share network

## Architecture

Plugin do rely on Jenkins Cloud API. Global configuration do only define a label, as slave template is actually declared in job configuration as a NodeProperty. 
Internally a slave image is defined and is responsible to establish jenkins remoting.

When a job is triggered, job configuration + remoting image do define a container group ("pod") the plugin has to run. ContainerProvisionner is responsible to run this pod. 

## [Docker](https://www.docker.com) implementation

Plugin includes a ContainerProvisionner implementation based on Docker CLI. 

This implementation do run the slave remoting container using a plain `docker run` command and rely on docker stdin/stdout as remoting transport (i.e. CommandLauncher or equivalent). 
The Launcher is decorated so command/process to be launched on the slave are directly executed with `docker exec`.

General idea is to avoid to use Jenkins remoting to launch processes but directly rely on Docker for this (what docker finally is is just an `execve` on steriods!). That magically brings long-running tasks for free.

## Kubernetes implementation

Kubernetes has native support for Pod concept, so would embrace this design with minimal effort.

## Amazon ECS implementation

Comparable to Kubernetes.

## Mesos implementation

To be considered

## [Rkt](https://github.com/coreos/rkt) implementation

Supporting rkt runtime could be great from a security POV. rkt is able to launch containers isolated inside a small KVM process, greatly enhancing security (https://coreos.com/blog/rkt-0.8-with-new-vm-support/)

## Other ideas

 * Browse workspace after build completion by running a fresh new container with columes-from build's data-container
 * Slave view do offer a terminal access to the slave environment. Could rely on https://wiki.jenkins-ci.org/display/JENKINS/Remote+Terminal+Access+Plugin
