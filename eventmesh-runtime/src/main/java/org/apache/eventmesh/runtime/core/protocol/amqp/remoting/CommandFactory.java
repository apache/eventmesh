package org.apache.eventmesh.runtime.core.protocol.amqp.remoting;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.AMQImpl;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ProtocolVersion;

import java.util.Map;

public class CommandFactory {

    private ProtocolVersion _protocolVersion;

    public CommandFactory(ProtocolVersion pv) {
        _protocolVersion = pv;
    }

    public void setProtocolVersion(final ProtocolVersion protocolVersion) {
        _protocolVersion = protocolVersion;
    }

    public final AMQP.Access.Request createAccessRequestBody(final String realm,
        final boolean exclusive,
        final boolean passive,
        final boolean active,
        final boolean write,
        final boolean read) {
        return new AMQImpl.Access.Request.Builder()
            .realm(realm)
            .exclusive(exclusive)
            .passive(passive)
            .active(active)
            .write(write)
            .read(read)
            .build();
    }

    public final AMQP.Access.RequestOk createAccessRequestOkBody(final int ticket) {
        return new AMQImpl.Access.RequestOk.Builder()
            .ticket(ticket)
            .build();
    }

    public final AMQP.Basic.Publish createBasicPublishBody(final int ticket,
        final String exchange,
        final String routingKey,
        final boolean mandatory,
        final boolean immediate) {
        return new AMQImpl.Basic.Publish.Builder()
            .ticket(ticket)
            .exchange(exchange)
            .routingKey(routingKey)
            .mandatory(mandatory)
            .immediate(immediate)
            .build();
    }

    public final AMQP.Basic.Ack createBasicAckBody(final long deliveryTag,
        final boolean multiple) {
        return new AMQImpl.Basic.Ack.Builder()
            .deliveryTag(deliveryTag)
            .multiple(multiple)
            .build();
    }

    public final AMQP.Basic.Nack createBasicNackBody(long deliveryTag,
        boolean multiple,
        boolean requeue) {
        return new AMQImpl.Basic.Nack.Builder()
            .deliveryTag(deliveryTag)
            .multiple(multiple)
            .requeue(requeue)
            .build();
    }

    public final AMQP.Basic.RecoverOk createBasicRecoverOkBody() {
        return new AMQImpl.Basic.RecoverOk();
    }

    public final AMQP.Basic.QosOk createBasicQosOkBody() {
        return new AMQImpl.Basic.QosOk();
    }

    public final AMQP.Basic.Consume createBasicConsumeBody(final int ticket,
        final String queue,
        final String consumerTag,
        final boolean noLocal,
        final boolean noAck,
        final boolean exclusive,
        final boolean nowait,
        final Map<String, Object> arguments) {

        return new AMQImpl.Basic.Consume.Builder()
            .ticket(ticket)
            .queue(queue)
            .consumerTag(consumerTag)
            .noLocal(noLocal)
            .noAck(noAck)
            .exclusive(exclusive)
            .nowait(nowait)
            .arguments(arguments)
            .build();
    }

    public final AMQP.Basic.ConsumeOk createBasicConsumeOkBody(final String consumerTag) {
        return new AMQImpl.Basic.ConsumeOk.Builder()
            .consumerTag(consumerTag)
            .build();
    }

    public final AMQP.Basic.Cancel createBasicCancelBody(final String consumerTag,
        final boolean nowait) {
        return new AMQImpl.Basic.Cancel.Builder()
            .consumerTag(consumerTag)
            .nowait(nowait)
            .build();
    }

    public final AMQP.Basic.CancelOk createBasicCancelOkBody(final String consumerTag) {
        return new AMQImpl.Basic.CancelOk.Builder()
            .consumerTag(consumerTag)
            .build();
    }

    public final AMQP.Basic.Return createBasicReturnBody(final int replyCode,
        final String replyText,
        final String exchange,
        final String routingKey) {
        return new AMQImpl.Basic.Return.Builder()
            .replyCode(replyCode)
            .replyText(replyText)
            .exchange(exchange)
            .routingKey(routingKey)
            .build();
    }

    public final AMQP.Basic.Deliver createBasicDeliverBody(final String consumerTag,
        final long deliveryTag,
        final boolean redelivered,
        final String exchange,
        final String routingKey) {
        return new AMQImpl.Basic.Deliver.Builder()
            .consumerTag(consumerTag)
            .deliveryTag(deliveryTag)
            .redelivered(redelivered)
            .exchange(exchange)
            .routingKey(routingKey)
            .build();
    }

    public final AMQP.Basic.Get createBasicGetBody(int ticket, String queue, boolean noAck) {
        return new AMQImpl.Basic.Get.Builder()
            .ticket(ticket)
            .queue(queue)
            .noAck(noAck)
            .build();
    }

    public final AMQP.Basic.GetOk createBasicGetOkBody(final long deliveryTag,
        final boolean redelivered,
        final String exchange,
        final String routingKey,
        final long messageCount) {
        return new AMQImpl.Basic.GetOk.Builder()
            .deliveryTag(deliveryTag)
            .redelivered(redelivered)
            .exchange(exchange)
            .routingKey(routingKey)
            .messageCount((int) messageCount)
            .build();
    }

    public final AMQP.Basic.GetEmpty createBasicGetEmptyBody(final String clusterId) {
        return new AMQImpl.Basic.GetEmpty.Builder()
            .clusterId(clusterId)
            .build();
    }

    public final AMQP.Channel.OpenOk createChannelOpenOkBody(int channelId) {
        return new AMQImpl.Channel.OpenOk.Builder()
            .channelId(Integer.toString(channelId))
            .build();
    }

    public final AMQP.Channel.FlowOk createChannelFlowOkBody(final boolean active) {
        return new AMQImpl.Channel.FlowOk.Builder()
            .active(active)
            .build();
    }

    public final AMQP.Channel.Close createChannelCloseBody(final int replyCode, final String replyText,
        final int classId,
        final int methodId
    ) {
        return new AMQImpl.Channel.Close.Builder()
            .replyCode(replyCode)
            .replyText(replyText)
            .classId(classId)
            .methodId(methodId)
            .build();
    }

    public final AMQP.Channel.CloseOk createChannelCloseOkBody() {
        return new AMQImpl.Channel.CloseOk();
    }

    public final AMQP.Channel.Open createChannelOpenBody(final String outOfBand) {
        return new AMQImpl.Channel.Open.Builder()
            .outOfBand(outOfBand)
            .build();
    }

    public final AMQP.Connection.Start createConnectionStartBody(final short versionMajor,
        final short versionMinor,
        final Map<String, Object> serverProperties,
        final String mechanisms,
        final String locales) {
        return new AMQImpl.Connection.Start.Builder()
            .versionMajor(versionMajor)
            .versionMinor(versionMinor)
            .serverProperties(serverProperties)
            .mechanisms(mechanisms)
            .locales(locales)
            .build();
    }

    public final AMQP.Connection.Secure createConnectionSecureBody(final String challenge) {
        return new AMQImpl.Connection.Secure.Builder()
            .challenge(challenge)
            .build();
    }

    public final AMQP.Connection.SecureOk createConnectionSecureOkBody(final String response) {
        return new AMQImpl.Connection.SecureOk.Builder()
            .response(response)
            .build();
    }

    public final AMQP.Connection.Tune createConnectionTuneBody(final int channelMax,
        final long frameMax,
        final int heartbeat) {
        return new AMQImpl.Connection.Tune.Builder()
            .channelMax(channelMax)
            .frameMax((int) frameMax)
            .heartbeat(heartbeat)
            .build();
    }

    public final AMQP.Connection.TuneOk createConnectionTuneOkBody(final int channelMax,
        final long frameMax,
        final int heartbeat) {
        return new AMQImpl.Connection.TuneOk.Builder()
            .channelMax(channelMax)
            .frameMax((int) frameMax)
            .heartbeat(heartbeat)
            .build();
    }

    public final AMQP.Connection.Open createConnectionOpenBody(final String virtualHost,
        final String capabilities,
        final boolean insist) {
        return new AMQImpl.Connection.Open.Builder()
            .virtualHost(virtualHost)
            .capabilities(capabilities)
            .insist(insist)
            .build();
    }

    public final AMQP.Connection.OpenOk createConnectionOpenOkBody(final String knownHosts) {
        return new AMQImpl.Connection.OpenOk.Builder()
            .knownHosts(knownHosts)
            .build();
    }

    public final AMQP.Connection.StartOk createConnectionStartOkBody(final Map<String, Object> clientProperties,
        final String mechanism,
        final String response,
        final String locale) {
        return new AMQImpl.Connection.StartOk.Builder()
            .clientProperties(clientProperties)
            .mechanism(mechanism)
            .response(response)
            .locale(locale)
            .build();
    }

    public final AMQP.Connection.CloseOk createConnectionCloseOkBody() {
        return new AMQImpl.Connection.CloseOk();
    }

    public final AMQP.Connection.Close createConnectionCloseBody(final int replyCode,
        final String replyText,
        final int classId,
        final int methodId) {
        return new AMQImpl.Connection.Close.Builder()
            .replyCode(replyCode)
            .replyText(replyText)
            .classId(classId)
            .methodId(methodId)
            .build();
    }

    public final AMQP.Exchange.Declare createExchangeDeclareBody(final int ticket,
        final String exchange,
        final String type,
        final boolean passive,
        final boolean durable,
        final boolean autoDelete,
        final boolean internal,
        final boolean nowait,
        final Map<String, Object> arguments) {

        return new AMQImpl.Exchange.Declare.Builder()
            .ticket(ticket)
            .exchange(exchange)
            .type(type)
            .passive(passive)
            .durable(durable)
            .autoDelete(autoDelete)
            .internal(internal)
            .nowait(nowait)
            .arguments(arguments)
            .build();
    }

    public final AMQP.Exchange.DeclareOk createExchangeDeclareOkBody() {
        return new AMQImpl.Exchange.DeclareOk();
    }

    public final AMQP.Exchange.Delete createExchangeDeleteBody(final int ticket,
        final String exchange,
        final boolean ifUnused,
        final boolean nowait) {
        return new AMQImpl.Exchange.Delete.Builder()
            .ticket(ticket)
            .exchange(exchange)
            .ifUnused(ifUnused)
            .nowait(nowait)
            .build();

    }

    public final AMQP.Exchange.DeleteOk createExchangeDeleteOkBody() {
        return new AMQImpl.Exchange.DeleteOk();
    }

    public final AMQP.Exchange.BindOk createExchangeBindOkBody() {
        return new AMQImpl.Exchange.BindOk();
    }

    public final AMQP.Queue.Declare createQueueDeclareBody(final int ticket,
        final String queue,
        final boolean passive,
        final boolean durable,
        final boolean exclusive,
        final boolean autoDelete,
        final boolean nowait,
        final Map<String, Object> arguments) {

        return new AMQImpl.Queue.Declare.Builder()
            .ticket(ticket)
            .queue(queue)
            .passive(passive)
            .durable(durable)
            .exclusive(exclusive)
            .autoDelete(autoDelete)
            .nowait(nowait)
            .arguments(arguments)
            .build();
    }

    public final AMQP.Queue.DeclareOk createQueueDeclareOkBody(final String queue,
        final int messageCount,
        final int consumerCount) {
        return new AMQImpl.Queue.DeclareOk.Builder()
            .queue(queue)
            .messageCount(messageCount)
            .consumerCount(consumerCount)
            .build();
    }

    public final AMQP.Queue.BindOk createQueueBindOkBody() {
        return new AMQImpl.Queue.BindOk();
    }

    public final AMQP.Queue.DeleteOk createQueueDeleteOkBody(final int messageCount) {
        return new AMQImpl.Queue.DeleteOk.Builder()
            .messageCount(messageCount)
            .build();
    }

    public final AMQP.Queue.Bind createQueueBindBody(final int ticket,
        final String queue,
        final String exchange,
        final String routingKey,
        final boolean nowait,
        final Map<String, Object> arguments) {

        return new AMQImpl.Queue.Bind.Builder()
            .ticket(ticket)
            .queue(queue)
            .exchange(exchange)
            .routingKey(routingKey)
            .nowait(nowait)
            .arguments(arguments)
            .build();
    }

    public final AMQP.Queue.UnbindOk createQueueUnbindOkBody() {
        return new AMQImpl.Queue.UnbindOk();
    }

    public final AMQP.Queue.Unbind createQueueUnbindBody(final int ticket,
        final String queue,
        final String exchange,
        final String routingKey,
        final Map<String, Object> arguments) {
        return new AMQP.Queue.Unbind.Builder()
            .ticket(ticket)
            .queue(queue)
            .exchange(exchange)
            .routingKey(routingKey)
            .arguments(arguments)
            .build();
    }

    public final AMQP.Queue.Purge createQueuePurgeBody(final int ticket,
        final String queue,
        final boolean nowait) {
        return new AMQImpl.Queue.Purge.Builder()
            .ticket(ticket)
            .queue(queue)
            .nowait(nowait)
            .build();
    }

    public final AMQP.Queue.PurgeOk createQueuePurgeOkBody(int messageCount) {
        return new AMQImpl.Queue.PurgeOk.Builder()
            .messageCount(messageCount)
            .build();
    }

    public final AMQP.Queue.Delete createQueueDeleteBody(final int ticket,
        final String queue,
        final boolean ifUnused,
        final boolean ifEmpty,
        final boolean nowait) {
        return new AMQImpl.Queue.Delete.Builder()
            .queue(queue)
            .ifUnused(ifUnused)
            .ifEmpty(ifEmpty)
            .nowait(nowait)
            .build();
    }

    public final AMQP.Confirm.SelectOk createConfirmSelectOkBody() {
        return new AMQImpl.Confirm.SelectOk();
    }

    public final AMQP.Confirm.Select createConfirmSelectBody(boolean nowait) {
        return new AMQImpl.Confirm.Select.Builder()
            .nowait(nowait)
            .build();
    }

    public final AMQP.Tx.SelectOk createTxSelectOkBody() {
        return new AMQImpl.Tx.SelectOk();
    }

    public final AMQP.Tx.CommitOk createTxCommitOkBody() {
        return new AMQImpl.Tx.CommitOk();
    }

    public final AMQP.Tx.RollbackOk createTxRollbackOkBody() {
        return new AMQImpl.Tx.RollbackOk();
    }

    public ProtocolVersion getProtocolVersion() {
        return _protocolVersion;
    }

}
