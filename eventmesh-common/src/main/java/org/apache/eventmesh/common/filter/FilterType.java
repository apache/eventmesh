package org.apache.eventmesh.common.filter;

public class FilterType {
    public static void main(String[] args) {
       String st =  "{\n" + "    \"source\": [\"mq\", \"ecs\", 123],\n"
                + "    \"aliyunregionid\": [\"cn-hangzhou\", {\"prefix\": \"cn-\"}],\n" + "    \"data\": {\n"
                + "        \"c-count\": {\n" + "            \"d-count\": [{\"numeric\": [\">\", 10]}],\n"
                + "            \"but\": [{\"anything-but\": \"started\"}],\n" + "            \"e-count\": {\n"
                + "                \"f-count\": [{\"exists\": false}]\n" + "            }\n" + "        },\n"
                + "        \"prefix\": [{\"prefix\": \"aliyun-\"}],\n"
                + "        \"suffix\": [{\"suffix\": \"-eventbridge\"}]\n" + "    }\n" + "}";
        System.out.println(st);
    }
}
