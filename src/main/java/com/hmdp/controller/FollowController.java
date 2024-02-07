package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author Zhang Haishuo
 * @since 2024-2-7
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable Long id, @PathVariable Boolean isFollow) {
        return followService.follow(id, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result follow(@PathVariable Long id) {
        return followService.isFollow(id);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable Long id) {
        return followService.followCommons(id);
    }
}
