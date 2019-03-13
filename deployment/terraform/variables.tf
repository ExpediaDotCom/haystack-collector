
variable "kafka_hostname" {}
variable "kafka_port" {}
variable "graphite_hostname" {}
variable "graphite_port" {}
variable "kubectl_context_name" {}
variable "kubectl_executable_name" {}
variable "app_namespace" {}
variable "node_selector_label"{}

# collectors config
variable "collector" {
  type = "map"
}
