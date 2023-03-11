package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        String code = RandomUtil.randomNumbers(6);
        // 将验证码存到session域当中
        session.setAttribute("code",code);
        log.debug("发送短信验证码成功，验证码{} ",code);
        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginFormDTO, HttpSession httpSession) {
        // 1.校验手机号
        String phone = loginFormDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式有误");
        }
        // 2.校验验证码
        Object cacheCode = httpSession.getAttribute("code");
        String code = loginFormDTO.getCode();
        if (code == null || !code.equals(cacheCode.toString())) {
            // 3.验证码错误
            return Result.fail("验证码错误");
        }
        // 4.验证码一致，根据手机号查询用户
        User user = this.query().eq("phone", loginFormDTO.getPhone()).one();
        if (user == null) {
            // 5.创建新用户
            user = createUserWithPhone(phone);
        }
        httpSession.setAttribute("user",user);
        return Result.ok(user.toString());
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("用户_" + RandomUtil.randomString(6));
        this.save(user);
        return user;
    }


}
