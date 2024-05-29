package com.apache.eventmesh.admin.server.web.db.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @TableName event_mesh_data_source
 */
@TableName(value ="event_mesh_data_source")
@Data
public class EventMeshDataSource implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer dataType;

    private String description;

    private String configuration;

    private Integer createUid;

    private Integer updateUid;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}