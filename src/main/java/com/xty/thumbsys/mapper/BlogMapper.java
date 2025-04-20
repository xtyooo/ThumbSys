package com.xty.thumbsys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xty.thumbsys.model.entity.Blog;

import java.util.Map;

/**
 * @author xtyooo
 */
public interface BlogMapper extends BaseMapper<Blog> {

    void batchUpdateThumbCount(Map<Long,Long> countMap);
}




