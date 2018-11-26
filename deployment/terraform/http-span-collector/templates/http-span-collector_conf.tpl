kafka {
  producer {
    topic = "proto-spans"
    props {
      bootstrap.servers = "${kafka_endpoint}"
      retries = 50
      batch.size = 153600
      linger.ms = 250
      compression.type = "lz4"
    }
  }
}

extractor {
  output.format = "proto"
}

http {
  host = "0.0.0.0"
  port = ${container_port}
}

