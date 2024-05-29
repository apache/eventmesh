package com.apache.eventmesh.admin.server.web.db.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @TableName event_mesh_runtime_heartbeat
 */
@TableName(value ="event_mesh_runtime_heartbeat")
@Data
public class EventMeshRuntimeHeartbeat implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String adminAddr;

    private String runtimeAddr;

    private Integer jobID;

    private String reportTime;

    private Date updateTime;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}