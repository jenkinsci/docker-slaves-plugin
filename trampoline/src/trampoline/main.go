package main

import (
	"fmt"
	"log"
	"os"
	"os/exec"
	"os/signal"
	"syscall"
)

var (
	// Version is a state variable, written at the link stage. See Makefile.
	Version string
	// CommitID is a state variable, written at the link stage. See Makefile.
	CommitID string
)

func usage() {
	fmt.Println(`usage: trampoline <subcommand>

Where subcommand can be:
	cdexec: Run a command after changing current working directory to the given directory
`)
}

func cdexec(args []string) {
	if len(args) < 2 {
		usage()
		os.Exit(1)
	}

	err := os.Chdir(args[0])
	if err != nil {
		log.Fatal(err)
		os.Exit(255)
	}

	binary, lookErr := exec.LookPath(args[1])
	if lookErr != nil {
		log.Fatal(lookErr)
		os.Exit(255)
	}

	if err := syscall.Exec(binary, args[1:], os.Environ()); err != nil {
		log.Fatal(err)
		os.Exit(255)
	}
}

func wait_system_signal() {
	channel := make(chan os.Signal)
	signal.Notify(channel)
	<-channel
}

func main() {
	if len(os.Args) < 2 {
		usage()
		os.Exit(1)
	}

	subCommand := os.Args[1]
	commandLine := os.Args[2:]

	switch subCommand {
	case "cdexec":
		cdexec(commandLine)
	case "wait":
		wait_system_signal()
	default:
		usage()
	}
}
