var AWS = require('aws-sdk');

var config = {
  "accessKeyId": "FAKE",
  "secretAccessKey": "FAKE",
  "region": "us-west-2",
  "kinesisEndpoint": "http://kinesis:4567",
  "kinesisPort": 4567,
  "StreamName": "haystack-proto-spans",
  "ShardCount": 1
};

var kinesis = new AWS.Kinesis({
  endpoint: config.kinesisEndpoint,
  accessKeyId: config.accessKeyId,
  secretAccessKey: config.secretAccessKey,
  region: config.region
});

AWS.config.update({});


kinesis.listStreams({ }, function(err, data) {
    if (err) throw err;

    console.log('Stream ready: ', data);

    if(data.StreamNames.includes(config.StreamName)) {
        console.log('Stream already exists');
    } else {
        kinesis.createStream({ StreamName: config.StreamName, ShardCount: config.ShardCount }, function (err) {
            if (err) throw err;

            kinesis.describeStream({ StreamName: config.StreamName }, function(err, data) {
                if (err) throw err;
                console.log('Stream ready: ', data);
            });
        });
    }
});

