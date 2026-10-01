package com.semple.aigc.canvas.api.aigc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.semple.aigc.canvas.api.aigc.domain.TeamMember;
import org.apache.ibatis.annotations.Mapper;

/** 团队成员 Mapper。 */
@Mapper
public interface TeamMemberMapper extends BaseMapper<TeamMember> {
}
