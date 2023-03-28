package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.annotation.Resources;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @description:TODO
 * @author:Povlean
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserServiceImpl userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 获取当前用户
        Long currentUserId = UserHolder.getUser().getId();
        String key = "follows:" + currentUserId;
        if (isFollow) {
            // 没有关注，那么就代表需要关注
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(currentUserId);
            boolean isSuccess = this.save(follow);
            if (isSuccess) {
                // 把关注用户的id存入到redis中
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        } else {
            // 关注了，那么就需要取消关注
            // SQL:delete from tb_follow where user_id = ? and follow_user_id = ?
            LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Follow::getUserId,currentUserId).eq(Follow::getFollowUserId,followUserId);
            boolean isSuccess = this.remove(queryWrapper);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        // 获取当前登录用户
        Long currentUserId = UserHolder.getUser().getId();
        // 在tb_follow表中查询，是否有数据存在
        // 判断数据是否存在，不需要返回真正的数据，只需要判断查找的条数是否大于零
        // SQL:select count(*) from tb_follow where user_id = ? and follow_user_id = ?
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getUserId,currentUserId)
                .eq(Follow::getFollowUserId,followUserId);
        Follow follow = this.getOne(queryWrapper);
        return Result.ok(follow != null);
    }

    @Override
    public Result followCommons(Long followUserId) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + userId;
        // 2.求交集
        String key2 = "follows:" + followUserId;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            // set集合中没有交集，表明没有共同关注
            // 返回一个空集合
            return Result.ok(Collections.emptyList());
        }
        // 3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 4.查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }

}
