package com.xty.thumbsys.constant;

public interface ThumbConstant {



    /**
     * 用户点赞 hash key
     */
    String USER_THUMB_KEY_PREFIX = "thumb:";

    /**
     * 临时点赞记录过期时间  30 天
     */
    Long THUMB_EXPIRE_TIME = 30 * 24 * 60 * 60 * 1000L;

    /**
     * 临时 点赞记录 key
     */
    String TEMP_THUMB_KEY_PREFIX = "thumb:temp:%s";
}
