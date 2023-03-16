# Redis 黑马点评

### 1. 编写拦截器

​	做一个登录校验的拦截器，用于拦截非法用户，该用户必须是数据库的user表可以查询出来的用户。

​	在utils包中定义一个LoginInterceptor

​	实现 HandlerIntercepter 的 preHandle() 

![image-20230311151714640](C:\Users\Asphyxia\AppData\Roaming\Typora\typora-user-images\image-20230311151714640.png)

​	实现 afterCompletion()

![image-20230311151734986](C:\Users\Asphyxia\AppData\Roaming\Typora\typora-user-images\image-20230311151734986.png)

​	在config包中创建一个MvcConfig类，该类用于添加我们的定义的拦截器。

​		需要实现 WebMvcConfigurer 类中的 addInterceptors

​		用该方法的形参注册器添加拦截器

![image-20230311151610206](C:\Users\Asphyxia\AppData\Roaming\Typora\typora-user-images\image-20230311151610206.png)

​	业务层controller

​	如果拦截器放行了登录业务，说明该用户是合法用户，因此拦截器会在校验的时候，将合法用户的信息存储在ThreadLocal中，在controller的校验中直接从ThreadLocal中获取用户信息即可。

![image-20230311151807249](C:\Users\Asphyxia\AppData\Roaming\Typora\typora-user-images\image-20230311151807249.png)