package com.xty.thumbsys.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.xty.thumbsys.model.dto.thumb.DoThumbRequest;
import com.xty.thumbsys.model.entity.Thumb;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @author xtyooo
 */
public interface ThumbService extends IService<Thumb> {

    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    Boolean hasThumb(Long blogId, Long userId);


}
