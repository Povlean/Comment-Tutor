package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void saveShop2Redis(Long id,Long expireSeconds) {
        // 1.查询店铺数据
        Shop shop = this.getById(id);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }

    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2.判断是否存在
        if(StringUtils.isNotBlank(shopJson)) {
            // 3.存在，则返回商铺信息
            // 返回序列化，将Redis中的JSON数据转化为Java对象Bean
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 条件过滤到这里只有两种情况，一种是null，一种是""空字符串
        if (shopJson != null) {
            // 如果为""，那么直接返回错误信息
            return null;
        }
        // 4.不存在，则查询数据库
        Shop shop = this.getById(id);
        if (shop == null) {
            // 将空值写入进redis，以防造成内存穿透
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            // 5.数据库中返回为空，则抛异常或返回错误
            return null;
        }
        // 6.在数据库中存在，则写入redis中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L,TimeUnit.MINUTES);
        // 7.将结果返回给前端
        return shop;
    }

    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2.判断是否存在
        if(StringUtils.isBlank(shopJson)) {
            // 3.不存在，直接返回
            return null;
        }
        // 4.命中，需要
        // 4.不存在，则查询数据库
        Shop shop = this.getById(id);

        // 6.在数据库中存在，则写入redis中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L,TimeUnit.MINUTES);
        // 7.将结果返回给前端
        return shop;
    }

    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2.判断是否存在
        if(StringUtils.isNotBlank(shopJson)) {
            // 3.存在，则返回商铺信息
            // 返回序列化，将Redis中的JSON数据转化为Java对象Bean
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 条件过滤到这里只有两种情况，一种是null，一种是""空字符串
        if (shopJson != null) {
            // 如果为""，那么直接返回错误信息
            return null;
        }
        // 4.实现缓存重建
        // 4.1.获取互斥锁
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2.判断是否获取成功
            if(!isLock) {
                // 4.3.失败，则进入休眠状态
                Thread.sleep(50);
                // 递归，仅需进入该方法
                return queryWithMutex(id);
            }
            // 4.4 成功，根据id查询数据库
            shop = this.getById(id);
            if (shop == null) {
                // 将空值写入进redis，以防造成内存穿透
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                // 5.数据库中返回为空，则抛异常或返回错误
                return null;
            }
            // 6.在数据库中存在，则写入redis中
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7.释放互斥锁
            unlock(lockKey);
        }
        // 8.将结果返回给前端
        return shop;
    }

    /**
    * @description:获取互斥锁
    * @author:Povlean
    * @date:2023/3/15 16:37
    * @param:* @param key
    * @return:* @return Boolean
    */
    private Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 避免包装类型在拆包的时候出现空指针异常
        return BooleanUtil.isTrue(flag);
    }

    /**
    * @description:释放互斥锁
    * @author:Povlean
     * @date:2023/3/15 16:36
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = this.queryWithPassThrough(id);
        // 互斥锁解决击穿
        // Shop shop = this.queryWithMutex(id);

        Shop shop = this.queryWithLogicalExpire(id);
        if (shop == null) {
            Result.fail("店铺不存在！");
        }
        // 状态返回
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id为空");
        }
        // 1.操作数据库
        this.updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        // 3.放行
        return Result.ok();
    }
}
