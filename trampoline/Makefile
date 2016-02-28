.PHONY: default clean build

GO=$(GOROOT)/bin/go
GOOS ?= $(shell GOROOT=$(GOROOT) $(GO) env GOOS)
GOARCH ?= $(shell GOROOT=$(GOROOT) $(GO) env GOARCH)
DISTDIR ?= $(CURDIR)/dist/$(GOOS)-$(GOARCH)
GO_BUILDFLAGS ?= -x -v

VERSION ?= 1.0
REV ?= $(shell git rev-parse --short HEAD)

ifdef VERSION
  GO_LDFLAGS := $(GO_LDFLAGS) -X main.Version=$(VERSION)
endif
ifdef REV
  GO_LDFLAGS := $(GO_LDFLAGS) -X main.CommitID=$(REV)
endif

default: build

clean:
	rm -rf $(DISTDIR)

build: $(DISTDIR)/trampoline

$(DISTDIR)/%:
	GOPATH=$(CURDIR) GOOS=$(GOOS) GOARCH=$(GOARCH) $(GO) build $(GO_BUILDFLAGS) -ldflags "$(GO_LDFLAGS)" -o $(DISTDIR)/$*.fat $*
	upx -o $(DISTDIR)/$* $(DISTDIR)/$*.fat
