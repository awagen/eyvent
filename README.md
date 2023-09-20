# eyvent
Lean event persister.


## What the project is good for and how this works

The project provides a lean event persister without the need for additional queues.
Storage initially is file-based and supports local storage as well as AWS s3 and GCP gcs.
It provides configuration options to both partition by group and file size and time buckets (such as hourly).
Further, it provides additional "flush-criteria" that determine when a new event file is written to the storage.
In case the service goes down, leftover event stores are attempted to be persisted, which should avoid data loss
in situations such as dynamic scaling leading to node downing.


## Endpoints

- POST: `event/[eventType]/[group]` - post events of formats as specified for the eventType
  - `[eventType]`: see config description for `EVENT_ENDPOINT_STRUCTDEF_PAIRS` below. Each first element of all derived tuples of event type and structural definition specifies a single endpoint, that accepts only events matching the structural definition.
  - `[group]`: must either be a value as configured in `VALID_GROUPS` (see below) or any value if the config value corresponding to the description below for `VALID_GROUPS` is set to `*`. 
- GET: `metrics` - endpoint to be scraped by prometheus


## Building, testing, assembly, docker image
NOTE: you will only be able to build the project if you locally publish
`kolibri-storage`, which is to be found in the project 
`https://github.com/awagen/kolibri`. Release of the jar to public repo is planned.
In the meantime you can do so in the root-folder of the above kolibri-project with
`sbt kolibri-storage/publishLocal`.

- recompile and test: `sbt clean test`
- building jar within target/scala-2.13/: `sbt assembly`
- creating docker image: `docker build . -t eyvent:0.0.1`


## Configuration
Without needing to alter the config file, the configuration properties can be set via env variable.
In this project you will find an example docker-compose.yml file.
The below gives an overview of configuration properties.

| ENV Variables - Optional configurations | Usage                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
|-----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| _NODE_HASH_                             | (Optional) Every node needs a unique identifier, which will also be reflected in the file names of the files written by any particular node to avoid clashes (they are unlikely even without this since the event file names also carry the timestamp when it was created). If not set, will be generated randomly.                                                                                                                                                                                                                                                                                                                                                                                           |
| _HTTP_SERVER_PORT_                      | (Optional) Port under which the service is started. Default: 6001.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| _NON_BLOCKING_POOL_THREADS_             | (Optional) In case you want to deviate from the default ZIO settings, you can set the number of non-blocking threads used here.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| _BLOCKING_POOL_THREADS_                 | (Optional) In case you want to deviate from the default ZIO settings, you can set the number of blocking threads used here.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| _STRUCT_DEF_SUB_FOLDER_                 | (Optional) Here you can set the folder from which the structural definitions of allowed events (json files) are picked. Default: "eventStructDefs". Note that as all path this holds relative to the base path configured.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| _EVENT_STORAGE_SUB_FOLDER_              | (Optional) Sub-folder (relative to the configured base folder) where the event partition directories are created in which the single event log files are persisted. Default: "events"                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| _PARTITIONING_                          | (Optional) Json giving the definition of the partitioning to be applied. See NamingPatternJsonProtocol for allowed values. Default: `{"type": "YEAR_MONTH_DAY_HOUR", "separator": "/"}` (which causes partitioning to be applied by yyyy/mm/dd/hh).                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| _VALID_GROUPS_                          | (Optional) Comma-separated values of groups allowed to use in the event post endpoint. If values contain `*`, this will cause for all groups to be accepted. Default: `*`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| _EVENT_ENDPOINT_STRUCTDEF_PAIRS_        | (Optional) Comma-separated values where two sequential values are interpreted as pairs. The first value defines the type of the event endpoint (and will be used in the generated endpoints) and the second defines the name of the json file that specifies how an event is allowed to look like (e.g which fields are needed and of which type). For description of the format see below. Note that the configured json files are searched for in the folder defined in `STRUCT_DEF_SUB_FOLDER`. This effectively binds an endpoint type to the type of jsons it accepts. Default: `store,simpleEvent1.json` (where simpleEvent1.json can be found in the examples folder and is a very simplified example) |
| _MAX_FILE_SIZE_IN_MB_                   | (Optional) Specifies the maximal file size in MB that any event log file should not exceed (note that the resulting size can slightly exceed this value, since the mechanism sums up the size and when the limit is met or exceeded flushes the file to storage). Default: 2.                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| _MAX_NUMBER_OF_EVENTS_                  | (Optional) Specifies the maximal number of events that any event log file should not exceed. Default: 5000.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |


| Env Variables - Storage configuration |                                                                                                                                                                                                                                                                                                                       |
|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| _PERSISTENCE_MODE_                    | The persistence mode used. Can be: AWS (s3), GCP (gcs), LOCAL (local file system), RESOURCE (local resources), CLASS (if selected, need to define `PERSISTENCE_MODULE_CLASS` property, specifying fully qualified name to used persistence module class.                                                              |
| _PERSISTENCE_MODULE_CLASS_            | If `PERSISTENCE_MODE` is set to `CLASS`, need to set here the fully qualified name to used persistence module class, such as `de.awagen.kolibri.fleet.zio.config.di.modules.persistence.LocalPersistenceModule` (which happens to refer to the same persistence module as just specifying PERSISTENCE_MODE as LOCAL). |
| _AWS_PROFILE_                         | If `PERSISTENCE_MODE` is `AWS` (or `CLASS` and the AWS module is referenced above), specify here the profile to use.                                                                                                                                                                                                  |
| _AWS_S3_BUCKET_                       | If AWS storage is used, define here the bucket to store tha state / result data in.                                                                                                                                                                                                                                   |
| _AWS_S3_PATH_                         | If AWS storage is used, define here the path within the above defined bucket to use as base path.                                                                                                                                                                                                                     |
| _AWS_S3_REGION_                       | If AWS storage is used, define the region here.                                                                                                                                                                                                                                                                       |
| _GCP_GS_BUCKET_                       | If GCP storage is used, define here the bucket to store tha state / result data in.                                                                                                                                                                                                                                   |
| _GCP_GS_PATH_                         | If GCP storage is used, define here the path within the above defined bucket to use as base path.                                                                                                                                                                                                                     |
| _GCP_GS_PROJECT_ID_                   | If GCP storage is used, define here the project id under which you created the bucket.                                                                                                                                                                                                                                |
| _LOCAL_STORAGE_WRITE_BASE_PATH_       | If LOCAL storage is used, define the base path here under which to store the data.                                                                                                                                                                                                                                    |
| _LOCAL_STORAGE_READ_BASE_PATH_        | If LOCAL storage is used, define the base path here from which data is read (should usually be the same as the write base path).                                                                                                                                                                                      |


## Defining accepted json formats for single endpoints

First of all, you need to define the format of the expected events for each endpoint
that accepts event messages.
The format is given by the json format of the distinct fields as represented by
`StructDef` instances (see `JsonStructDefs` for the definitions and `JsonStructDefsJsonProtocol`
for the json format of these definitions). Here we will give a short tour on the format:

- the StructDef information is represented as json object
- the `type` of the top-level element is `NESTED`, which refers to the fact that the expected
  event is itself a json with single fields in it.
- since the top-level element is of type `NESTED`, it has additional attributes 
  `fields` (the fields that do not depend on any other set field) and `conditionalFieldsSeq`,
  which represents a sequence of fields that depend on the value of any of the fields specified
  in `fields`.
- within an element of type `NESTED`, each single field has the following attributes:
  - `nameFormat` that defines how the key value needs to look (in case of a constant 
    identifier, which will be the most common case, it would be of type `STRING_CONSTANT`)
  - `valueFormat` that defines how the value for the key specified by `nameFormat` is supposed to look (e.g which type, which restrictions).
  - `required`: boolean flag that defines whether the field must be set of can be left out.
    Note that validations on a field where required=false is only applied if the value is set.
    Leaving it out altogether counts as valid.

Now the attributes that go into `nameFormat` and `valueFormat` refer to the same set of 
value definitions, and the attributes to set depend on the value of the respective `type`
attribute. Let's see what we have here:


### Primitive Types

| Type                  | Description                                                              | Fields / Examples                                                          |
|-----------------------|--------------------------------------------------------------------------|----------------------------------------------------------------------------|
| _INT_                 | Any integer value.                                                       | {"type": "INT"}                                                            |
| _CHOICE_INT_          | Any of a selection of integer values.                                    | {"type": "CHOICE_INT", "choices": [0, 1, 2]}                               |
| _MIN_MAX_INT_         | Any integer within [min, max].                                           | {"type": "MIN_MAX_INT", "min": 0, "max": 5}                                |
| _STRING_CONSTANT_     | String of exactly the value defined by `value` attribute.                | {"type": "STRING_CONSTANT", "value": "const1"}                             |
| _STRING_              | Any string.                                                              | {"type": "STRING"}                                                         |
| _CHOICE_STRING_       | Any of a selection of string values.                                     | {"type": "CHOICE_STRING", "choices": ["str1", "str2"]}                     |
| _REGEX_               | Any string matching the regex given by the `regex` attribute.            | {"type": "REGEX", "regex": ".*"}                                           |
| _FLOAT_               | Any float.                                                               | {"type": "FLOAT"}                                                          |
| _CHOICE_FLOAT_        | Any of a selection of float values.                                      | {"type": "CHOICE_FLOAT", "choices": [0.4, 0.5, 0.6]}                       |
| _MIN_MAX_FLOAT_       | Any float within [min, max].                                             | {"type": "MIN_MAX_FLOAT", "min": 0.1, "max": 0.5}                          |
| _DOUBLE_              | Any double.                                                              | {"type": "DOUBLE"}                                                         |
| _CHOICE_DOUBLE_       | Any of a selection of double values.                                     | {"type": "CHOICE_DOUBLE", "choices": [0.4, 0.5, 0.6]}                      |
| _MIN_MAX_DOUBLE_      | Any double within [min, max].                                            | {"type": "MIN_MAX_DOUBLE", "min": 0.1, "max": 0.5}                         |
| _BOOLEAN_             | Any boolean.                                                             | {"type": "BOOLEAN"}                                                        |
| _EITHER_OF_           | Any value matching any of the formats given by the attribute `formats`.  | {"type": "EITHER_OF", "formats": [{"type": "STRING"}, {"type": "DOUBLE"}]} |




### Collections

| Type                    | Description                                                                   | Fields / Examples                                                   |
|-------------------------|-------------------------------------------------------------------------------|---------------------------------------------------------------------|
| _INT_SEQ_               | Sequence of any integers.                                                     | {"type": "INT_SEQ"}                                                 |
| _SEQ_CHOICE_INT_        | Sequence where each element is one of the given integer choices.              | {"type": "SEQ_CHOICE_INT", "choices": [0, 1, 2]}                    |
| _SEQ_CHOICE_FLOAT_      | Sequence where each element is one of the given float choices.                | {"type": "SEQ_CHOICE_FLOAT", "choices": [0.1, 0.2, 1.2]}            |
| _SEQ_CHOICE_DOUBLE_     | Sequence where each element is one of the given double choices.               | {"type": "SEQ_CHOICE_DOUBLE", "choices": [0.1, 0.2, 1.2]}           |
| _STRING_SEQ_            | Sequence of any strings.                                                      | {"type": "STRING_SEQ"}                                              |
| _SEQ_CHOICE_STRING_     | Sequence where each element is one of the given string choices.               | {"type": "SEQ_CHOICE_STRING", "choices": ["str1", "str2"]}          |
| _SEQ_REGEX_             | Sequence where each element is a string matching the given regex.             | {"type": "SEQ_REGEX", "regex": ".*"}                                |
| _GENERIC_SEQ_FORMAT_    | Sequence where each element is one of the values given by `perElementFormat`. | {"type": "GENERIC_SEQ_FORMAT", "perElementFormat": {"type": "INT"}} |
| _SEQ_MIN_MAX_INT_       | Sequence where each element is an integer within [min, max].                  | {"type": "SEQ_MIN_MAX_INT", "min": 4, "max": 10}                    |
| _SEQ_MIN_MAX_FLOAT_     | Sequence where each element is an float within [min, max].                    | {"type": "SEQ_MIN_MAX_FLOAT", "min": 0.4, "max": 10}                |
| _SEQ_MIN_MAX_DOUBLE_    | Sequence where each element is an double within [min, max].                   | {"type": "SEQ_MIN_MAX_DOUBLE", "min": 0.4, "max": 10}               |


### Maps

| Type           | Description                                                                                                                                                                                                                                                            | Fields                                                                                                                                                                                                                                                                                                                                                     |
|----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| _NESTED_       | Specifies the format of all unconditional fields and mappings for specific values of selected unconditional fields to conditional fields. This specifies a direct dependency of the value selected for the unconditional fields and the respective conditional fields. | {"type": "NESTED", "fields": [{"nameFormat": {"type": "STRING_CONSTANT", "value": "field1"}, "valueFormat": {"type": "REGEX", "regex": ".*"}}], "conditionalFieldsSeq": [{"conditionalFieldId": "field1", "mapping": {"value1": [{"nameFormat": {"type": "STRING_CONSTANT", "value": "condField1"}, "required": true, "valueFormat": {"type": "INT"}}]}]}  |
| _MAP_          | Specifies formats for the keys and values in a map.                                                                                                                                                                                                                    | {"type": "MAP", "keyFormat": {"type": "STRING"}, "valueFormat": {"type": "INT"}}                                                                                                                                                                                                                                                                           |


### App Downing Behavior
The service tries to persist all leftover event stores when app is shut down via `.onExit` hook.
This should avoid loosing any events.