package com.apache.eventmesh.admin.server.web.db.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @TableName event_mesh_job_info
 */
@TableName(value ="event_mesh_job_info")
@Data
public class EventMeshJobInfo implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer jobID;

    private String name;

    private Integer transportType;

    private Integer sourceData;

    private Integer targetData;

    private Integer state;

    private Integer jobType;

    private Integer createUid;

    private Integer updateUid;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}