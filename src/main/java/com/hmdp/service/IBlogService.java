package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Zhang Haishuo
 * @since 2024-2-6
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogById(Long id);
}
