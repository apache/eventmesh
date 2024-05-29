package com.apache.eventmesh.admin.server.web.db.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @TableName event_mesh_runtime_history
 */
@TableName(value ="event_mesh_runtime_history")
@Data
public class EventMeshRuntimeHistory implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer job;

    private String address;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}