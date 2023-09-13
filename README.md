# eyvent
Lean event persister.


## What the project is good for and how this works

The project provides a lean event persister without the need for additional queues.
Storage initially is file-based and supports local storage as well as AWS s3 and GCP gcs.
It provides configuration options to both partition by group as well as file size and time buckets (such as hourly).
Further, it provides additional "flush-criteria" that determine when a new event file is written to the storage.
