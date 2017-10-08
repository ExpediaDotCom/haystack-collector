.PHONY: all kinesis

PWD := $(shell pwd)

clean:
	mvn clean

build: clean
	mvn package

report-coverage:
	docker run -it -v ~/.m2:/root/.m2 -w /src -v `pwd`:/src maven:3.5.0-jdk-8 mvn scoverage:report-only

all: clean kinesis report-coverage

kinesis:
	mvn package -pl kinesis -am
	cd kinesis && $(MAKE) integration_test

# build all and release
release: all
	cd kinesis && $(MAKE) release
