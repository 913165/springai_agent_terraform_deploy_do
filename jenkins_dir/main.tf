terraform {
  required_providers {
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
  }
}

provider "null" {}

variable "jenkins_url" {
  description = "Jenkins server URL"
  type        = string
  default     = "http://172.27.122.70:8080"
}

variable "jenkins_job" {
  description = "Jenkins job name"
  type        = string
  default     = "spring-test-deployment"
}

variable "jenkins_user" {
  description = "Jenkins username"
  type        = string
  default     = "tinumistry"
}

variable "jenkins_token" {
  description = "Jenkins API token"
  type        = string
  sensitive   = true
}

resource "null_resource" "trigger_jenkins" {

  triggers = {
    deployment = timestamp()
  }

  provisioner "local-exec" {

    interpreter = [
      "C:/Program Files/Git/bin/bash.exe",
      "-c"
    ]

    command = <<-EOT
      curl.exe -X POST \
        -u "${var.jenkins_user}:${var.jenkins_token}" \
        "${var.jenkins_url}/job/${var.jenkins_job}/build"
    EOT
  }
}