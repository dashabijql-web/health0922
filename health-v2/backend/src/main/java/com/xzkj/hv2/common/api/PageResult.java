package com.xzkj.hv2.common.api;

import java.util.List;

/** 分页接口的 data：{ total, page, size, list }，总数由后端算（docs/01 第五节）。 */
public record PageResult<T>(long total, int page, int size, List<T> list) {
}
