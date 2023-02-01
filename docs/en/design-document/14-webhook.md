
## Webhook usage process
#### The first step: Configure webhook related information in eventmesh and start

##### Configuration
```
# Whether to start the webhook admin service
eventMesh.webHook.admin.start=true

# webhook event configuration storage mode. But currently only supports file and nacos
eventMesh.webHook.operationMode=file

# The file path of fileMode. If you write #{eventMeshHome}, in the eventMesh root directory
eventMesh.webHook.fileMode.filePath= #{eventMeshHome}/webhook

# The nacos storage mode. The configuration naming rule is eventMesh.webHook.nacosMode.{nacos native configuration key} For the specific configuration, please see [nacos github api](https://github.com/alibaba/nacos/blob/develop/api/src/main/java /com/alibaba/nacos/api/SystemPropertyKeyConst.java)
## address of nacos
eventMesh.webHook.nacosMode.serverAddr=127.0.0.1:8848

# webhook eventcloud send mode. Same as eventMesh.connector.plugin.type configuration
eventMesh.webHook.producer.connector=standalone
```

#### The second step: Add webhook configuration information

Configuration information description

```java
   /**
    * The path called by the manufacturer. Manufacturer event call address, [http or https]://[domain or IP]:[port]/webhook/[callbackPath]
    * for example: http://127.0.0.1:10504/webhook/test/event , The full url needs to be filled in the manufacturer call input
    * callbackPath is the only
    */
    private String callbackPath;

    /**
     * manufacturer name, like github
     */
    private String manufacturerName;

    /**
     * webhook event name, like rep-push
     */
    private String manufacturerEventName;

    /**
     * http header content type
     */
    private String contentType = "application/json";

    /**
     * description of this WebHookConfig
     */
    private String description;

    /**
     * secret key, for authentication
     */
    private String secret;

    /**
     * userName, for HTTP authentication
     */
    private String userName;

    /**
     * password, for HTTP authentication
     */
    private String password;


    /**
     * roll out event name, like topic to mq
     */
    private String cloudEventName;

    /**
     * roll out data format -> CloudEvent serialization mode
     * If HTTP protocol is used, the request header contentType needs to be marked
     */
    private String dataContentType = "application/json";;

    /**
     * source of event
     */
    private String cloudEventSource;

    /**
     * id of cloudEvent, like uuid/manufacturerEventId
     */
    private String cloudEventIdGenerateMode;

```

##### Add webhook config

path: /webhook/insertWebHookConfig
method: POST
contentType: application/json

input params:
| field | desc | type |　necessary | default　|
| -- | -- | -- | -- | -- |
| callbackPath | call address, unique address | string | Y　| null　|
| manufacturerName | manufacturer name | string | Y　| null　|
| manufacturerEventName | manufacturer EventName  | string | Y　| null　|
| contentType | http connettype | string | N　| application/json　|
| description | configuration instructions | string | N　| null　|
| secret | signature string | string | N　| null　|
| userName | username | string | N　| null　|
| password | password | string | N　| null　|
| cloudEventName | cloudEvent name  | string | Y　| null　|
| cloudEventSource | cloudEvent source | string | Y　| null　|
| cloudEventIdGenerateMode | cloudEvent event object identification method, uuid or event id  | string | N manufacturerEventId　|

E.g:

```json

{
	"callbackPath":"/webhook/github/eventmesh/all",
	"manufacturerName":"github",
	"manufacturerEventName":"all",
	"secret":"eventmesh",
	"cloudEventName":"github-eventmesh",
	"cloudEventSource":"github"
}

```

Output params: 1 for success, 0 for failure

##### delete webhook config
path: /webhook/deleteWebHookConfig
method: POST
contentType： application/json

input params:
| field | desc | type |　necessary | default　|
| -- | -- | -- | -- | -- |
| callbackPath | call address, unique address | string | Y　| null　|


E.g:

```json

{
	"callbackPath":"/webhook/github/eventmesh/all"
}

```

Output params: 1 for success, 0 for failure

##### select WebHookConfig by callbackPath
path: /webhook/queryWebHookConfigById
method: POST
contentType： application/json

input params:
| field | desc | type |　necessary | default　|
| -- | -- | -- | -- | -- |
| callbackPath | call address, unique address | string | Y　| null　|


E.g:

```json

{
	"callbackPath":"/webhook/github/eventmesh/all"
}

```

Output params:
| field | desc | type |　necessary | default　|
| -- | -- | -- | -- | -- |
| callbackPath | call address, unique address | string | Y　| null　|
| manufacturerName | manufacturer name | string | Y　| null　|
| manufacturerEventName | manufacturer event name | string | Y　| null　|
| contentType | http connettype | string | N　| application/json　|
| description | configuration instructions | string | N　| null　|
| secret | signature key | string | N　| null　|
| userName | user name | string | N　| null　|
| password | password | string | N　| null　|
| cloudEventName | cloudEvent name | string | Y　| null　|
| cloudEventSource | cloudEvent source | string | Y　| null　|
| cloudEventIdGenerateMode | cloudEvent event object identification method, uuid or event id | string | N　| manufacturerEventId　|


##### 通过manufacturer查询WebHookConfig列表
path: /webhook/queryWebHookConfigByManufacturer
method: POST
contentType： application/json

input params:
| field | desc | type |　necessary | default　|
| -- | -- | -- | -- | -- |
| manufacturerName | manufacturer name | string | Y　| null　|


E.g:

```json

{
	"manufacturerName":"github"
}

```

Output params:
| field | desc | type |　necessary | default　|
| -- | -- | -- | -- | -- |
| callbackPath | call address, unique address | string | Y　| null　|
| manufacturerName | manufacturer name | string | Y　| null　|
| manufacturerEventName | manufacturer event name | string | Y　| null　|
| contentType | http connettype | string | N　| application/json　|
| description | configuration instructions | string | N　| null　|
| secret | signature key | string | N　| null　|
| userName | user name | string | N　| null　|
| password | password | string | N　| null　|
| cloudEventName | cloudEvent name | string | Y　| null　|
| cloudEventSource | cloudEvent source | string | Y　| null　|
| cloudEventIdGenerateMode | cloudEvent event object identification method, uuid or event id  | string | N　| manufacturerEventId　|


#### The third step: Check if the configuration is successful

1. file storage mode. Please go to the eventMesh.webHook.fileMode.filePath directory to view. filename callbackPath.

2. nacos storage mode. Please go to the nacos service configured by eventMesh.webHook.nacosMode.serverAddr to see.

#### The fourth step: Configure the consumer of cloudevent

#### The fifth step: Configure webhook related information in the manufacturer

> For manufacturer's operation, please refer to [Manufacturer's webhook operation instructions] .

## Manufacturer's webhook operation instructions

### github sign up

#### The first step: Enter the corresponding project

#### The second step: click setting

![](/images/design-document/webhook/webhook-github-setting.png)

#### The third step: click Webhooks

![](/images/design-document/webhook/webhook-github-webhooks.png)

#### The fourth step: Click on Add webhook

![](/images/design-document/webhook/webhook-github-add.png)

#### The fifth step: Fill in the webhook information

![](/images/design-document/webhook/webhook-github-info.png)

Payload URL: Service address and pahts. [http or https]://[domain or IP]:[port]/webhook/[callbackPath]
Content type: http header content type
secret: signature string




