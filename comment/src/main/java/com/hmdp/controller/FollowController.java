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
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService iFollowService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId,@PathVariable("isFollow") Boolean isFollow) {
        return iFollowService.follow(followUserId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return iFollowService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long followUserId) {
        if (followUserId == null || followUserId <= 0) {
            return Result.fail("followUserId error");
        }
        return iFollowService.followCommons(followUserId);
    }


}
