package com.semple.aigc.canvas.common.core.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 公共实体时间字段自动填充处理器。
 *
 * @author zengzhewen
 */
@Component
public class BaseEntityMetaObjectHandler implements MetaObjectHandler {

    /**
     * 新增数据时填充创建时间和更新时间。
     *
     * @param metaObject MyBatis-Plus 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        Date now = new Date();
        // 项目逻辑删除约定为 1=未删除，新增数据显式写入，避免受旧表默认值影响。
        if (metaObject.hasSetter("deleted") && metaObject.getValue("deleted") == null) {
            metaObject.setValue("deleted", 1);
        }
        this.strictInsertFill(metaObject, "createTime", Date.class, now);
        this.strictInsertFill(metaObject, "updateTime", Date.class, now);
    }

    /**
     * 更新数据时填充更新时间。
     *
     * @param metaObject MyBatis-Plus 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
    }
}
