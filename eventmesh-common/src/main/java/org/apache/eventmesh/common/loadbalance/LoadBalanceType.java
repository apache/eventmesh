package org.apache.eventmesh.common.loadbalance;

public enum LoadBalanceType {
    RANDOM(0, "random load balance strategy"),
    WEIGHT_ROUND_ROBIN(1, "weight round robin load balance strategy");

    private int code;
    private String desc;

    LoadBalanceType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

}
