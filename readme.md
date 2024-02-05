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

（3）使用乐观锁解决秒杀超卖问题。

（4）使用悲观锁解决单体项目的一人一单问题。

（5）使用`redis` 实现分布式锁——版本1。

（6）解决不同线程的锁误删除问题。

（7）使用`lua` 脚本解决原子性问题。

（8）使用`Redisson` 便捷实现锁功能。

（9）使用阻塞队列将秒杀下单同步执行过程优化成异步执行。

此部分有待改进。因为：1.阻塞队列容易满，内存溢出了。2.数据存在`JVM` 上，如果突然宕机，数据就全都丢失了。



