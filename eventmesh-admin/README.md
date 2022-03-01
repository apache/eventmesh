# EventMesh Administration Module

EventMesh Administration Module for EventMesh. It manages Admin Service, Configurations such as topics/subscriptions management.It works as a control plane and provide some interface to manage the eventmesh-runtime module and other configurations.

## Administration Client Manager APIs

### POST /topicManage
- Create a new topic if does not exist
- Exposed POST endpoint to create a new topic if it does not exist.
    * Url - http://localhost:8081/topicManage
    * sample request payload 
     ```json
        {
            "name":  "mytopic1"
        }
     ```

   Sample response 

   ```json
   {
        "topic": "mytopic1",
        "created_time": "2021-09-03",
   }
   ```
### DELETE /topicManage/(string: topic)
- Delete a specific topic.
- Exposed DELETE endpoint to remove a specific topic
    * URL -     
    ```url 
    http://localhost:8081/topicManage/mytopic1
    ```
    
    * Response - 
    
   ```json
   {
        "topic": "mytopic1",        
   }
   ```

### GET /topicManage 
- Retrieve a list of topics
- Exposed GET endpoint to retrieve all topics
    * URL - 
    ```url 
    http://localhost:8081/topicManage
    ```
    * Response 
    
   ```json
   ["mytopic1", "mytopic2"]
   ```
