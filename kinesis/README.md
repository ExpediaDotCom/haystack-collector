# haystack-kinesis-span-collector
This haystack component reads the batch of spans from kinesis stream and publish them to kafka topic.
It expects the [Batch](https://github.com/ExpediaDotCom/haystack-idl/blob/master/proto/span.proto) protobuf object in the stream.
It deserializes this proto object and use each span's TraceId as the partition key for writing to the kafka topic. 
This component uses the [KCL](http://docs.aws.amazon.com/streams/latest/dev/developing-consumers-with-kcl.html#kinesis-record-processor-overview-kcl) 
library to build the pipeline for reading from kinesis stream and writing to kafka using high level kafka consumer api.

##Required Reading
 
In order to understand the haystack, we recommend to read the details of [haystack](https://github.com/ExpediaDotCom/haystack) project. 

##Technical Details
Fill this as we go along..

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

Run the integration tests with
```
make integration_test

```