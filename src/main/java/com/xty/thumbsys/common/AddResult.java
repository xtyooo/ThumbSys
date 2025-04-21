package com.xty.thumbsys.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AddResult {
    // 被淘汰的key
    private final String expelledKey;
    //当前的Key是否进入TopK
    private final boolean isHotKey;
    // 当前操作的key
    private final String currentKey;
}
