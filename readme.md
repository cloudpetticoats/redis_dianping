# 这是一个伟大的史诗级巨著！！

## 短信登录模块
使用`redis` 实现存储验证码、存储用户信息，并使用两个拦截器对用户信息的有效期进行刷新。

## 商户查询缓存
（1）更新缓存保证数据一致性最好采用先更新数据库，再删除缓存。这样可以使发生数据不一致的情况降到最低。

（2）添加事物注解使二者同步更新。

（3）缓存穿透两种解决办法：缓存空对象、布隆过滤。

（4）缓存穿透、雪崩、击穿相关知识点：https://zhuanlan.zhihu.com/p/323249499

## 优惠券秒杀
（1）使用`redis` 实现全局唯一ID生成器。

（2）实现秒杀的下单功能。



