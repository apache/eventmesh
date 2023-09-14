## Dashboard

To enable eventmesh-dashboard admin UI for RabbitMQ, you need to add plugin rabbitmq_management.

```bash
rabbitmq-plugins enable rabbitmq_management
```

## RabbitMQ Management

The RabbitMQ management UI can be accessed using a Web browser at `http://{node-hostname}:15672/`.

Users must be [granted permissions](https://www.rabbitmq.com/management.html#permissions) for management UI access.

The following example creates a user with complete access to the management UI/HTTP API (as in, all virtual hosts and management features):

```bash
# create a user
rabbitmqctl add_user full_access s3crEt
# tag the user with "administrator" for full management UI and HTTP API access
rabbitmqctl set_user_tags full_access administrator
```

eventmesh-dashboard does not support authenticating with OAuth 2 currently. If you are using OAuth 2 to authenticate, you should keep `management.disable_basic_auth` configuration at default value `false` to support HTTP basic authentication.

> More information for developers to provide admin functions: https://github.com/apache/eventmesh/pull/4395
