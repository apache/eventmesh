package cn.webank.defibus.broker.plugin;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PluginStoreStatServiceTest {

    private PluginStoreStatService pluginStoreStatService = new PluginStoreStatService();

    @Before
    public void init() {
        pluginStoreStatService.start();
    }

    @Test
    public void testRecordPutTime() {
        long value = 999;
        while (true) {
            pluginStoreStatService.recordPutTime(value);
            value = value * 10;
            if (value > 1000000000)
                break;
        }
        pluginStoreStatService.printStoreStat();
    }

    @Test
    public void testRecordGetTime() {
        long value = 999;
        while (true) {
            pluginStoreStatService.recordGetTime(value);
            value = value * 10;
            if (value > 1000000000)
                break;
        }
        pluginStoreStatService.printStoreStat();
    }

    @After
    public void shutDown() {
        pluginStoreStatService.shutdown();
    }
}
