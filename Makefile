.PHONY: all kinesis

PWD := $(shell pwd)

clean:
	mvn clean

build: clean
	mvn package

report-coverage:
	docker run -it -v ~/.m2:/root/.m2 -w /src -v `pwd`:/src maven:3.5.0-jdk-8 mvn scoverage:report-only

all: clean kinesis report-coverage

build_kinesis:
	mvn package -DfinalName=haystack-kinesis-span-collector -pl kinesis -am

# build all and release
release: clean build_kinesis
	cd kinesis && $(MAKE) release
	./.travis/deploy.sh
