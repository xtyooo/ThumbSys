package com.xty.thumbsys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xty.thumbsys.model.entity.Blog;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * @author xtyooo
 */
public interface BlogMapper extends BaseMapper<Blog> {

    void batchUpdateThumbCount(@Param("countMap") Map<Long,Long> countMap);
}




