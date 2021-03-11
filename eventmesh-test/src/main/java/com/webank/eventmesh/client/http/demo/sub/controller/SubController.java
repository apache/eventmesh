package com.webank.eventmesh.client.http.demo.sub.controller;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/sub")
public class SubController {

    public static Logger logger = LoggerFactory.getLogger(SubController.class);

    @RequestMapping(value = "/test", method = RequestMethod.POST)
    public String subTest(@RequestBody String message){
        logger.info("=======receive message======= {}", JSONObject.toJSONString(message));
        JSONObject result = new JSONObject();
        result.put("retCode", 1);
        return result.toJSONString();
    }

}
