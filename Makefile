.PHONY: all kinesis

PWD := $(shell pwd)

clean:
	mvn clean

build: clean
	mvn package

all: clean kinesis

kinesis:
	mvn package -pl kinesis -am
	cd kinesis && $(MAKE) integration_test

# build all and release
release: all
	cd kinesis && $(MAKE) release
