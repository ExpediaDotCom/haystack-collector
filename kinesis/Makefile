.PHONY: integration_test release

export DOCKER_ORG := expediadotcom
export DOCKER_IMAGE_NAME := haystack-kinesis-span-collector
PWD := $(shell pwd)

docker_build:
	docker build -t $(DOCKER_IMAGE_NAME) -f build/docker/Dockerfile .

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
         -e "HAYSTACK_PROP_KINESIS_ENDPOINT=http://kinesis:4567" \
         -e "HAYSTACK_PROP_KINESIS_DYNAMODB_ENDPOINT=http://dynamo:8000" \
         -e "HAYSTACK_PROP_KINESIS_STREAM_NAME=haystack-proto-spans" \
         -e "HAYSTACK_PROP_KAFKA_PRODUCER_PROPS_BOOTSTRAP_SERVERS=kafkasvc:9092" \
         -e "AWS_ACCESS_KEY=fake" \
         -e "AWS_SECRET_KEY=fake" \
         -e "AWS_CBOR_DISABLE=1" \
         $(DOCKER_IMAGE_NAME)

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
		-w /src \
		maven:3.5.0-jdk-8 \
		mvn test -pl kinesis -am -P integration-tests

	# stop all the containers
	docker-compose -f build/integration-tests/docker-compose.yml -p sandbox stop
	docker stop kinesis-collector
	docker rm $(shell docker ps -a -q)
	docker volume rm $(shell docker volume ls -q)

release: docker_build
	../scripts/publish-to-docker-hub.sh
