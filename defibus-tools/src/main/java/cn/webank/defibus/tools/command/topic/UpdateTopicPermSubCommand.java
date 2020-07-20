package cn.webank.defibus.tools.command.topic;

import cn.webank.defibus.tools.admin.DeFiBusAdminExt;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.protocol.body.ClusterInfo;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.QueueData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.remoting.exception.RemotingSendRequestException;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.tools.command.SubCommand;

import java.io.File;
import java.util.*;

public class UpdateTopicPermSubCommand implements SubCommand {
    ClusterInfo clusterInfo = null;

    @Override
    public String commandName() {
        return "updateTopicPerm";
    }

    @Override
    public String commandDesc() {
        return "Update topic perm";
    }

    @Override
    public Options buildCommandlineOptions(Options options) {
        Option opt = new Option("b", "brokerAddr", true, "create topic to which broker");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("t", "topic", true, "topic name");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("f", "topicList", true, "read the topic list by file path, split with '\\n'");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("s", "sleetTime", true, "sleep time between create two topic(ms)");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("p", "perm", true, "set topic's permission(2|4|6), intro[2:W; 4:R; 6:RW]");
        opt.setRequired(true);
        options.addOption(opt);

        return options;
    }

    @Override
    public void execute(final CommandLine commandLine, final Options options, RPCHook rpcHook) {
        DeFiBusAdminExt deFiBusAdminExt = new DeFiBusAdminExt(rpcHook, 3000L);
        deFiBusAdminExt.setInstanceName(Long.toString(System.currentTimeMillis()));
        try {
            deFiBusAdminExt.start();
            TopicConfig topicConfig = new TopicConfig();

            long sleepTime = 1000;
            if (commandLine.hasOption("s")) {
                sleepTime = Long.parseLong(commandLine.getOptionValue("s").trim());
            }

            List<String> topicList = new ArrayList<>();
            if (commandLine.hasOption("t")) {
                String[] topicArr = commandLine.getOptionValue("t").trim().split(";");
                topicList = Arrays.asList(topicArr);
            } else if (commandLine.hasOption("f")) {
                String path = commandLine.getOptionValue("f").trim();
                topicList = FileUtils.readLines(new File(path));
            }
            for (String topic : topicList) {
                try {
                    updateTopicPerm(topic, deFiBusAdminExt, topicConfig, sleepTime, commandLine);
                } catch (Exception e) {
                    System.out.println("[WARN] update topic[" + topic + "] perm failed ,exception info: " + e.getMessage());
                    try {
                        updateTopicPerm(topic, deFiBusAdminExt, topicConfig, sleepTime, commandLine);
                    } catch (Exception e1) {
                        System.out.println("[WARN] try again ,update topic[" + topic + "] perm failed,exception info: " + e1.getMessage());
                    }
                }
            }
//            ServerUtil.printCommandLineHelp("mqadmin " + this.commandName(), options);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            deFiBusAdminExt.shutdown();
        }
        return;
    }

    private void updateTopicPerm(String topic, DeFiBusAdminExt deFiBusAdminExt, TopicConfig topicConfig, long sleepTime, CommandLine commandLine) throws InterruptedException, MQClientException, RemotingException, MQBrokerException {
        TopicRouteData topicRouteData = deFiBusAdminExt.examineTopicRouteInfo(topic);
        assert topicRouteData != null;
        List<QueueData> queueDatas = topicRouteData.getQueueDatas();
        assert queueDatas != null && queueDatas.size() > 0;

        Set<String> brokerAddrs = new HashSet<>();
        if (commandLine.hasOption('b')) {
            brokerAddrs.add(commandLine.getOptionValue('b').trim());
        } else {
            brokerAddrs = fetchMasterAddrByTopic(deFiBusAdminExt, topic);
        }
        if (brokerAddrs.size() == 0) {
            System.out.println("[WARN] can not get brokerAddr for topic[" + topic + "]");
            return;
        }
        for (String brokerAddr : brokerAddrs) {
            String brokerName = getBrokerNameByBrokerAddr(deFiBusAdminExt, brokerAddr);
            QueueData queueData = null;
            for (QueueData queueDataTemp : queueDatas) {
                if (brokerName.equals(queueDataTemp.getBrokerName())) {
                    queueData = queueDataTemp;
                    break;
                }
            }
            if (queueData == null) {
                System.out.println("[WARN] topic[" + topic + "] get queueData failed for brokerAddr[" + brokerAddr + "]");
                return;
            }
            topicConfig.setTopicName(topic);
            topicConfig.setWriteQueueNums(queueData.getWriteQueueNums());
            topicConfig.setReadQueueNums(queueData.getReadQueueNums());
            topicConfig.setPerm(queueData.getPerm());
            topicConfig.setTopicSysFlag(queueData.getTopicSynFlag());

            //new perm
            int perm = Integer.parseInt(commandLine.getOptionValue("p").trim());
            int oldPerm = topicConfig.getPerm();
            if (perm == oldPerm) {
                System.out.printf("new perm equals to the old one!%n");
            }
            topicConfig.setPerm(perm);
            deFiBusAdminExt.createAndUpdateTopicConfig(brokerAddr, topicConfig);
            Thread.sleep(sleepTime);
            System.out.printf("update topic[%s] perm from %s to %s in %s success.%n", topic, oldPerm, perm, brokerAddr);
        }
    }

    private Set<String> fetchMasterAddrByTopic(DeFiBusAdminExt defaultMQAdminExt, String topic) throws RemotingException, MQClientException, InterruptedException {
        Set<String> masterSet = new HashSet<>();
        TopicRouteData topicRouteData = defaultMQAdminExt.examineTopicRouteInfo(topic);
        for (BrokerData bd : topicRouteData.getBrokerDatas()) {
            String masterAddr = bd.getBrokerAddrs().get(MixAll.MASTER_ID);
            if (masterAddr != null) {
                masterSet.add(masterAddr);
            } else {
                System.out.println("there is no master alive in " + bd.getBrokerName() + ", skip this group");
            }
        }

        return masterSet;
    }

    private String getBrokerNameByBrokerAddr(DeFiBusAdminExt deFiBusAdminExt, String brokerAddr) throws InterruptedException, MQBrokerException, RemotingTimeoutException, RemotingSendRequestException, RemotingConnectException {
        String brokerName = null;
        if (clusterInfo == null) {
            clusterInfo = deFiBusAdminExt.examineBrokerClusterInfo();
        }
        Map<String, BrokerData> brokerDataMap = clusterInfo.getBrokerAddrTable();
        for (Map.Entry<String, BrokerData> entry : brokerDataMap.entrySet()) {
            Map<Long, String> brokerAddrs = entry.getValue().getBrokerAddrs();
            if (brokerAddrs.containsValue(brokerAddr)) {
                brokerName = entry.getKey();
                break;
            }
        }
        if (brokerName == null) {
            System.out.println("can not get brokerName for brokerAddr[" + brokerAddr + "]");
            System.exit(-1);
        } else {
            System.out.println("brokerAddr[" + brokerAddr + "]'s name is " + brokerName);
        }
        return brokerName;
    }
}
