package connector.rocketmq.config;

import com.webank.runtime.configuration.PropInit;
import io.openmessaging.KeyValue;
import io.openmessaging.OMS;
import io.openmessaging.OMSBuiltinKeys;

public class PropInitImpl implements PropInit {

    public final String DEFAULT_ACCESS_DRIVER = "connector.rocketmq.MessagingAccessPointImpl";

    @Override
    public KeyValue initProp() {
        return OMS.newKeyValue().put(OMSBuiltinKeys.DRIVER_IMPL, DEFAULT_ACCESS_DRIVER);
    }
}
