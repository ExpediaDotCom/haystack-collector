.PHONY: all integration_test release

DOCKER_IMAGE_TAG := haystack-kinesis-span-collector
PWD := $(shell pwd)

clean:
	mvn clean

build:  clean
	mvn package

docker_build:
	docker build -t $(DOCKER_IMAGE_TAG) -f build/docker/Dockerfile .

prepare_integration_test_env: docker_build
	docker-compose -f build/integration-tests/docker-compose.yml -p sandbox up -d

	# kafka sometimes take time to start
	sleep 30

    # create the stream and dynamodb table
	docker run \
    		-it --network=sandbox_default \
    		-e "AWS_CBOR_DISABLE=1" \
    		-v $(PWD)/build/integration-tests/scripts:/scripts \
    		-w /scripts \
    		node:6.11.3 \
    		./setup.sh

	docker run \
	     --name kinesis-collector \
         -d --network=sandbox_default \
         -e "HAYSTACK_KINESIS_ENDPOINT=http://kinesis:4567" \
         -e "HAYSTACK_KINESIS_DYNAMODB_ENDPOINT=http://dynamo:8000" \
         -e "HAYSTACK_KINESIS_STREAM_NAME=haystack-proto-spans" \
         -e "HAYSTACK_KAFKA_PRODUCER_PROPS_BOOTSTRAP_SERVERS=kafkasvc:9092" \
         -e "AWS_ACCESS_KEY=fake" \
         -e "AWS_SECRET_KEY=fake" \
         -e "AWS_CBOR_DISABLE=1" \
         $(DOCKER_IMAGE_TAG)

	sleep 15

integration_test: prepare_integration_test_env
	# run tests in a container so that we can join the docker-compose network and talk to kafka and kinesis
	docker run \
		-it \
		--network=sandbox_default \
		-e "AWS_CBOR_DISABLE=1" \
		-e "AWS_ACCESS_KEY=fake" \
		-e "AWS_SECRET_KEY=fake" \
		-v $(PWD)/..:/src \
		-v ~/.m2:/root/.m2 \
		-w /src/kinesis \
		maven:3.5.0-jdk-8 \
		mvn test -P integration-tests

	# stop all the containers
	docker-compose -f build/integration-tests/docker-compose.yml -p sandbox stop
	docker stop kinesis-collector
	docker rm kinesis-collector

all: build docker_build integration_test

release: all
	./build/docker/publish-to-docker-hub.sh
