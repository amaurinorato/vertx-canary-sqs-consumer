# vertx-canary-sqs-consumer

Project to emulate a canary sqs consumer.

The goal is to deploy a verticle, consuming from a canary sqs queue and then, after validations, turn off the feature flag on launch darkly and re-deploy the same verticle, but now consuming from main queue.
