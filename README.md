[![Build Status](https://travis-ci.org/ExpediaDotCom/haystack-collector.svg?branch=master)](https://travis-ci.org/ExpediaDotCom/haystack-collector)

# haystack-collector
haystack component that collects spans from various sources and publish to kafka. Till today, we have kinesis span collector that reads from kinesis stream and writes the spans to kafka topic


## Building

####
Since this repo contains haystack-idl as the submodule, so use the following to clone the repo
* git clone --recursive git@github.com:ExpediaDotCom/haystack-collector.git .

####Prerequisite: 

* Make sure you have Java 1.8
* Make sure you have maven 3.3.9 or higher
* Make sure you have docker 1.13 or higher


Note : For mac users you can download docker for mac to set you up for the last two steps.

####Build

For a full build, including unit tests and integration tests, docker image build, you can run -
```
make all
```

####Integration Test

####Prerequisite:
1. Install docker using Docker Tools or native docker if on mac
2. Verify if docker-compose is installed by running following command else install it.
```
docker-compose

```

Run the build and integration tests for individual components with
```
make kinesis

```
