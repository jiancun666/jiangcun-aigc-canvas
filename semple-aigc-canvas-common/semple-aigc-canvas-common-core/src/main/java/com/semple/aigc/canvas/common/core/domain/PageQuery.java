package com.semple.aigc.canvas.common.core.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * Basic page query object.
 *
 * @author aofaming
 */
@Data
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    private int pageNum = 1;

    private int pageSize = 10;

    private String startTime;

    private String endTime;
}
