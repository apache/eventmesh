package org.apache.eventmesh.runtime.core.protocol.amqp.exchange;

import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.runtime.core.protocol.amqp.exchange.topic.TopicMatcherResult;
import org.apache.eventmesh.runtime.core.protocol.amqp.exchange.topic.TopicParser;
import org.apache.eventmesh.runtime.core.protocol.amqp.metadata.model.BindingInfo;

import java.util.Collection;
import java.util.Set;

@Slf4j
public class AmqpMessageRouteComponent implements RouteComponent {

    private DirectMessageRouter directMessageRouter;

    private FanoutMessageRouter fanoutMessageRouter;

    private TopicMessageRouter topicMessageRouter;


    public AmqpMessageRouteComponent() {
        this.directMessageRouter = new DirectMessageRouter();
        this.fanoutMessageRouter = new FanoutMessageRouter();
        this.topicMessageRouter = new TopicMessageRouter();
    }

    @Override
    public RoutingResult routMessage(Set<BindingInfo> bindingInfos, AmqpRouter.Type type,
                                     String routingKey) {
        RoutingResult routingResult = new RoutingResult();
        if (bindingInfos == null) {
            return routingResult;
        }
        AmqpRouter router = getRouter(type);
        if (router == null) {
            return routingResult;
        }

        bindingInfos.forEach(bindingInfo -> {
            if (router.isMatch(bindingInfo, routingKey)) {
                routingResult.addQueue(bindingInfo.getDestination());
            }
        });
        return routingResult;
    }

    public AmqpRouter getRouter(AmqpRouter.Type type) {

        if (type == null) {
            return null;
        }

        switch (type) {
            case Direct:
                return directMessageRouter;
            case Fanout:
                return fanoutMessageRouter;
            case Topic:
                return topicMessageRouter;
            default:
                return null;
        }

    }

    public class DirectMessageRouter implements AmqpRouter {

        @Override
        public boolean isMatch(BindingInfo bindingInfo, String routingKey) {
            return bindingInfo.getBindingKey().equals(routingKey);
        }
    }

    public class FanoutMessageRouter implements AmqpRouter {
        @Override
        public boolean isMatch(BindingInfo bindingInfo, String routingKey) {
            return true;
        }
    }

    public class TopicMessageRouter implements AmqpRouter {

        @Override
        public boolean isMatch(BindingInfo bindingInfo, String routingKey) {
            if (routingKey == null) {
                return false;
            }
            TopicParser parser = new TopicParser();
            parser.addBinding(bindingInfo.getBindingKey(), null);
            Collection<TopicMatcherResult> results = parser.parse(routingKey);
            if (results.size() > 0) {
                return true;
            } else {
                return false;
            }
        }
    }


}
