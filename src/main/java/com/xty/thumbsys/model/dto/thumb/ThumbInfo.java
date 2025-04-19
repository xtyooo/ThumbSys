package com.xty.thumbsys.model.dto.thumb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;

@Getter
@Data
public class ThumbInfo {
    @JsonProperty("thumbId")
    private Long thumbId;

    @JsonProperty("expireTime")
    private Long expireTime;
}