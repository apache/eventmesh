package org.apache.eventmesh.admin.server.web.db.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @TableName event_mesh_task_info
 */
@TableName(value ="event_mesh_task_info")
@Data
public class EventMeshTaskInfo implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;
    @TableId(type = IdType.ASSIGN_UUID)
    private String taskID;

    private String name;

    private String desc;

    private String transportType;

    private Integer sourceData;

    private Integer targetData;

    private String state;

    private Integer createUid;

    private Integer updateUid;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}