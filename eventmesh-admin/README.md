# EventMesh Administration Module

EventMesh Administration Module for EventMesh. It manages Admin and Schema Service, Configurations.It works as a control plane and provide some interface to manage the eventmesh-runtime module and other configurations.

## Schema Registry Service APIs

### POST /schemaregistry/subjects/(string: subject)/
- Register a new subject if does not exist
- Exposed POST endpoint to create a new subject if subject's topic does not exist. Engine stores the incoming JSON subject model into a database.
    * Url - http://localhost:8081/schemaregistry/subjects/test-topic
    * sample request payload 
     ```json
        {
            "subject": "test-topic",
            "namespace": "org.apache.rocketmq",
            "tenant": "messaging/rocketmq",
            "app": "rocketmq",
            "description": "rocketmq user information",
            "compatibility": "NONE",
            "status": "deprecated"
        }
     ```

   Sample response 

   ```json
   {
        "subject": "test-topic",
        "namespace": "org.apache.rocketmq",
        "tenant": "messaging/rocketmq",
        "app": "rocketmq",
        "description": "rocketmq user information",
        "compatibility": "NONE",
        "status": "deprecated"
   }
   ```

### POST /schemaregistry/subjects/(string: subject)/versions
- Register a new version of schema which belongs to the specific subject
- Exposed POST endpoint to register a new version of schema if same schema definition does not exist. Engine stores the incoming JSON schema model into a database.
    * Url - http://localhost:8081/schemaregistry/subjects/test-topic/versions
    * sample request payload 
     ```{
            "serialization": "PB",
            "schemaType": "AVRO",
            "schemaDefinition": [
                {
                    "name": "id",
                    "type": "string"
                },
                {
                    "name": "amount",
                    "type": "double"
                }
            ]
        }
     ```

   Sample response 

   ```json
   {
        "id": "a10-b10-c10"
   }
   ```

### POST /schemaregistry/compatibility/subjects/(string: subject)/versions/(version: version)
- Create a POST request to test if new schema payload is compatible against the target schema version
- Exposed POST endpoint to verify compatibility of a new schema payload.
    * Url - http://localhost:8081/schemaregistry/compatibility/test-topic/versions/1
    * sample request payload 
     ```{
            "schema": {
                "type": "string"}"
            }
        }
     ```

   Sample response 

   ```json
   {
        "is_compatible": true
   }
   ```

### PUT /schemaregistry/config/(string: subject)
- Update compatibility setting of a subject.
- Exposed PUT endpoint to modify an compatibility setting of a subject in a database.
    * Url - http://localhost:8081/schemaregistry/config/test-topic
    * sample request payload 
     ```json
        {
            "compatibility": "NONE" 
        }
     ```

   Sample response 

   ```json
    {    
        "compatibility": "NONE"
    }
   ```

### DELETE /schemaregistrys/subjects/(string: subject)/versions/(version: version)
- Delete a specific version of schema.
- Exposed DELETE endpoint to remove a specific schema version from the database
    * URL -     
    ```url 
    http://localhost:8081/schemaregistrys/subjects/test-topic/versions/1
    ```
    
    * Response - 
    
   ```json
    {1}
   ```

### GET /schemaregistrys/subjects 
- Retrieve a list of subjects
- Exposed GET endpoint to retrieve all subjects in database
    * URL - 
    ```url 
    http://localhost:8081/schemaregistry/subjects
    ```
    * Response 
    
   ```json
   ["subject1", "subject2"]
   ```

### GET /schemaregistrys/subjects/(string: subject)
- Retrieve a subject detail by subject's name
- Exposed GET endpoint to retrieve the subject detail's JSON representation given the reference subject name
    * URL -     
    ```url 
    http://localhost:8081/schemaregistrys/subjects/(string: subject)
    ```
    
    * Response -
    
   ```json
    {
        "subject": "test-topic",
        "namespace": "org.apache.rocketmq",
        "tenant": "messaging/rocketmq",
        "app": "rocketmq",
        "description": "JSON",
        "compatibility": "NONE"
    }
  ```
### GET /schemaregistrys/subjects/(string: subject)/versions
- Retrieve a subject detail by subject's name
- Exposed GET endpoint to retrieve the version numbers which belong to the given subject name
    * URL - 
    ```url 
    http://localhost:8081/schemaregistrys/subjects/(string: subject)/versions
    ```
    * Response -
    
   ```json
    [1, 2, 3, 4]
  ```

### GET /schemaregistrys/subjects/(string: subject)/versions/(version: version)/schema
- Retrieve subject and schema version details by subject's name and version number
- Exposed GET endpoint to retrieve the subject and schema version detail's JSON representation
    * URL - 
    ```url 
    http://localhost:8081/schemaregistrys/subjects/(string: subject)/versions/(version: version)/schema
    ```
    * Response -
    
   ```json
    {
        "subject": "test-topic",
        "namespace": "org.apache.rocketmq",
        "tenant": "messaging/rocketmq",
        "app": "rocketmq",
        "description": "rocketmq user information",
        "compatibility": "NONE",
        "schema": {
            "version": 1,
            "id": "20",
            "serialization": "PB",
            "schemaType": "AVRO",
            "schemaDefinition": [
                {
                    "name": "id",
                    "type": "string"
                },
                {
                    "name": "amount",
                    "type": "double"
                }
            ],
            "validator": "a.groovy",
            "comment": "rocketmq user information"
        }
    }
  ```

### GET /schemaregistrys/schemas/ids/{string: id}
- Retrieve a schema version detail by schema ID
- Exposed GET endpoint to retrieve the schema version detail's JSON representation
    * URL - 
    ```url 
    http://localhost:8081/schemaregistrys/schemas/ids/{string: id}
    ```
    * Response -
    
   ```json
    {
        "version": 1,
        "id": "20",
        "serialization": "PB",
        "schemaType": "AVRO",
        "schemaDefinition": [{
                "name": "id",
                "type": "string"
            },
            {
                "name": "age",
                "type": "short"
            }
        ],
        "validator": "a.groovy",
        "comment": "user information"
    }
  ```

### GET /schemaregistrys/schemas/ids/{string: id}/subjects
- Retrieve a subject name and version number based on schema id
- Exposed GET endpoint to retrieve subject name and version number
    * URL - 
    ```url 
    http://localhost:8081/schemaregistrys/schemas/ids/{string: id}
    ```
    * Response -
    
   ```json
    [
        {
            "subject":"test-topic",
            "version":1
        }
    ]            
  ```


### GET /schemaregistrys/config/(string: subject)
- Retrieve a subject's compatibility setting
- Exposed GET endpoint to retrieve subject's compatibility setting
    * URL - 
    ```url 
    http://localhost:8081/schemaregistrys/config/(string: subject)
    ```
    * Response -
    
   ```json    
    {
        "compatibility":"FULL"
    }    
  ```